package ai.hackerai.companion.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

class ConfigBackupTest {
    private val now = 1_760_920_000_000L
    private val trusted = CallerIdentity(uid = 10123, holdsBindPermission = true)
    private val live = CompanionConfig("claude-haiku-4-5-20251001", "https://api.anthropic.com/v1/messages")
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var n = 0

    private fun gate() = RemoteCommandGate(clock = { now }, externalControlEnabled = true)
    private fun cmd(op: String, args: String = "{}") = BoundCompanionCommand(
        requestId = "r", command = op, argsJson = args, nonce = "%032x".format(++n), timestampMs = now, targetNodeRef = "hackerai",
    )
    private fun q(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()

    private fun exportBundle(clear: Boolean = false): String {
        val r = ConfigBackup.export(gate(), trusted, RemoteCommandGate.CONTROLLER,
            cmd(ConfigBackup.OP_EXPORT, if (clear) """{"clearPayload":"true"}""" else "{}"), live, now, key)
        assertEquals("ok", r.code)
        return r.payloadJson
    }

    private fun importArgs(bundle: String, dryRun: Boolean = false, policy: String = "overwrite") =
        """{"bundle":${q(bundle)},"dryRun":"$dryRun","conflictPolicy":"$policy"}"""

    @Test fun `descriptors match spec`() {
        val d = RemoteCommandGate.DESCRIPTORS.associateBy { it.name }
        assertEquals(OpDescriptor("config.export", "action", false, false), d["config.export"])
        assertEquals(OpDescriptor("config.import", "action", false, true), d["config.import"])
    }

    @Test fun `export never contains keys or chat`() {
        listOf(exportBundle(clear = true), exportBundle()).forEach { p ->
            val l = p.lowercase()
            listOf("api_key", "apikey", "sk-", "token", "secret", "password", "chat", "prompt", "externalcontrol")
                .forEach { assertFalse("leaked $it", it in l) }
        }
        assertTrue("model" in exportBundle(clear = true))
    }

    @Test fun `export refuses a credential-bearing endpoint`() {
        val r = ConfigBackup.export(gate(), trusted, RemoteCommandGate.CONTROLLER, cmd(ConfigBackup.OP_EXPORT),
            live.copy(endpoint = "https://u:p@x.test/v1?key=abc"), now, key)
        assertEquals(RemoteErrorCode.INVALID_PARAMETERS, r.code)
    }

    @Test fun `export without key refuses instead of clear text`() {
        val r = ConfigBackup.export(gate(), trusted, RemoteCommandGate.CONTROLLER, cmd(ConfigBackup.OP_EXPORT), live, now, null)
        assertEquals(RemoteErrorCode.INVALID_PARAMETERS, r.code)
    }

    @Test fun `remoteAllowed false is enforced for both ops`() {
        assertEquals(RemoteErrorCode.CAPABILITY_OFF,
            ConfigBackup.export(gate(), trusted, "victorlaunch", cmd(ConfigBackup.OP_EXPORT), live, now, key).code)
        assertEquals(RemoteErrorCode.CAPABILITY_OFF,
            ConfigBackup.import(gate(), trusted, "victorlaunch", cmd(ConfigBackup.OP_IMPORT, importArgs(exportBundle())), live, key, true) {}.code)
    }

    @Test fun `import without confirm returns confirmation_required`() {
        var applied = false
        val r = ConfigBackup.import(gate(), trusted, RemoteCommandGate.CONTROLLER,
            cmd(ConfigBackup.OP_IMPORT, importArgs(exportBundle())), live, key, confirmed = false) { applied = true }
        assertEquals(RemoteErrorCode.CONFIRMATION_REQUIRED, r.code)
        assertFalse(applied)
    }

    @Test fun `dry run does not mutate`() {
        val b = ConfigBackup.export(gate(), trusted, RemoteCommandGate.CONTROLLER,
            cmd(ConfigBackup.OP_EXPORT, """{"clearPayload":"true"}"""), live.copy(model = "other-model"), now, key).payloadJson
        var applied = false
        val g = gate()
        val r = ConfigBackup.import(g, trusted, RemoteCommandGate.CONTROLLER,
            cmd(ConfigBackup.OP_IMPORT, importArgs(b, dryRun = true)), live, key, confirmed = false) { applied = true }
        assertEquals("ok", r.code)
        assertTrue("\"wouldChange\":true" in r.payloadJson)
        assertFalse(applied)
    }

    @Test fun `confirmed overwrite applies and encrypted round trip works`() {
        val b = ConfigBackup.export(gate(), trusted, RemoteCommandGate.CONTROLLER, cmd(ConfigBackup.OP_EXPORT),
            live.copy(model = "m2"), now, key).payloadJson
        var applied: CompanionConfig? = null
        val r = ConfigBackup.import(gate(), trusted, RemoteCommandGate.CONTROLLER, cmd(ConfigBackup.OP_IMPORT, importArgs(b)), live, key, true) { applied = it }
        assertEquals("ok", r.code)
        assertEquals("m2", applied?.model)
    }

    @Test fun `default skip policy keeps live config`() {
        var applied = false
        ConfigBackup.import(gate(), trusted, RemoteCommandGate.CONTROLLER,
            cmd(ConfigBackup.OP_IMPORT, """{"bundle":${q(exportBundle())}}"""), live.copy(model = "x"), key, true) { applied = true }
        assertFalse(applied)
    }

    @Test fun `import never enables remote command`() {
        val payload = """{"model":"m","endpoint":"https://x.test/v1","externalControlEnabled":true,"remoteCommandEnabled":true}"""
        val bundle = """{"version":1,"createdAtMs":$now,"sourceNodeRef":"controller","wrapping":"none","entries":[{"packageName":"ai.hackerai.companion","schemaVersion":1,"clearPayload":${q(payload)}}]}"""
        val g = gate()
        val r = ConfigBackup.import(g, trusted, RemoteCommandGate.CONTROLLER, cmd(ConfigBackup.OP_IMPORT, importArgs(bundle)), live, key, true) {}
        assertEquals("ok", r.code)
        assertFalse(g.externalControlEnabled)
    }

    @Test fun `import refuses secret names`() {
        val payload = """{"model":"m","endpoint":"https://x.test/v1","apiKey":"sk-1"}"""
        val bundle = """{"version":1,"createdAtMs":$now,"sourceNodeRef":"controller","wrapping":"none","entries":[{"packageName":"ai.hackerai.companion","schemaVersion":1,"clearPayload":${q(payload)}}]}"""
        var applied = false
        val r = ConfigBackup.import(gate(), trusted, RemoteCommandGate.CONTROLLER, cmd(ConfigBackup.OP_IMPORT, importArgs(bundle)), live, key, true) { applied = true }
        assertEquals(RemoteErrorCode.INVALID_PARAMETERS, r.code)
        assertFalse(applied)
    }

    @Test fun `tampered aad fails closed`() {
        assertNull(ConfigBackup.decrypt(key, ConfigBackup.encrypt(key, "{}", "a|1|x".toByteArray()), "a|1|y".toByteArray()))
    }
}
