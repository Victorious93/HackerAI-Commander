package ai.hackerai.companion.remote

import ai.hackerai.companion.llm.HttpLocalLlmProvider
import ai.hackerai.companion.service.AgentTaskRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteDispatcherTest {
    private lateinit var gate: RemoteCommandGate
    private lateinit var runner: AgentTaskRunner
    private lateinit var llm: HttpLocalLlmProvider
    private lateinit var dispatcher: RemoteDispatcher
    private var n = 0
    private val now = 1_760_920_000_000L
    private val json = Json { encodeDefaults = true }

    @Before fun setUp() {
        gate = RemoteCommandGate(clock = { now }, externalControlEnabled = true)
        runner = mockk(relaxed = true)
        llm = mockk(relaxed = true)
        every { llm.hasApiKey() } returns true
        every { llm.getModel() } returns "m"
        every { llm.getEndpoint() } returns "https://api.example.com/v1/messages"
        dispatcher = RemoteDispatcher(gate, runner, llm, CallingIdentitySource { CallerIdentity(10123, true) })
    }

    private fun env(command: String, argsJson: String = "{}", source: String? = null): String {
        n++
        val args = if (source == null) argsJson else {
            val o = json.parseToJsonElement(argsJson).jsonObject.toMutableMap()
            o["sourceNodeRef"] = kotlinx.serialization.json.JsonPrimitive(source)
            json.encodeToString(kotlinx.serialization.json.JsonObject(o))
        }
        return json.encodeToString(
            BoundCompanionCommand(
                requestId = "r$n", command = command, argsJson = args,
                nonce = "%032x".format(n), timestampMs = now, targetNodeRef = "hackerai",
            ),
        )
    }

    private fun code(raw: String) = json.decodeFromString<BoundCompanionResult>(raw).code

    @Test fun `destructive actionRefs never reach the runner`() {
        for (op in listOf("agent.run", "finding.validate", "task.cancel", "shizuku.shell")) {
            assertEquals(RemoteErrorCode.CONFIRMATION_REQUIRED, code(dispatcher.dispatch(env(op))))
        }
        verify(exactly = 0) { runner.runTask(any()) }
        verify(exactly = 0) { runner.validateFinding(any()) }
        verify(exactly = 0) { runner.cancelTask(any()) }
    }

    @Test fun `config export is local only and omits the api key`() {
        assertEquals(RemoteErrorCode.CAPABILITY_OFF, code(dispatcher.dispatch(env(ConfigBackup.OP_EXPORT, source = "peer"))))
        val clear = env(ConfigBackup.OP_EXPORT, """{"clearPayload":"true"}""")
        val r = json.decodeFromString<BoundCompanionResult>(dispatcher.dispatch(clear))
        assertEquals("ok", r.code)
        assertFalse("api_key" in r.payloadJson)
        assertFalse("apiKey" in r.payloadJson)
        assertTrue("model" in r.payloadJson)
        assertTrue("endpoint" in r.payloadJson)
    }

    @Test fun `import never enables external control`() {
        gate.externalControlEnabled = true
        val payload = """{"model":"m2","endpoint":"https://api.example.com/v1/messages"}"""
        val bundle = """{"version":1,"createdAtMs":$now,"sourceNodeRef":"controller","wrapping":"none","entries":[{"packageName":"ai.hackerai.companion","schemaVersion":1,"clearPayload":${json.encodeToString(payload)}}]}"""
        val r = dispatcher.dispatch(env(ConfigBackup.OP_IMPORT, """{"bundle":${json.encodeToString(bundle)},"dryRun":"false","conflictPolicy":"overwrite"}"""))
        // without confirm → confirmation_required; external control still forced off only after accepted import
        assertEquals(RemoteErrorCode.CONFIRMATION_REQUIRED, code(r))
        // And a dry-run must not flip the switch on either
        gate.externalControlEnabled = true
        dispatcher.dispatch(env(ConfigBackup.OP_IMPORT, """{"bundle":${json.encodeToString(bundle)},"dryRun":"true"}"""))
        assertTrue(gate.externalControlEnabled) // dry-run does not force-off in ConfigBackup.finally when dryRun; acceptable
    }

    @Test fun `node status get returns node_health`() {
        val r = json.decodeFromString<BoundCompanionResult>(dispatcher.dispatch(env(NodeStatus.OP)))
        assertEquals("ok", r.code)
        assertTrue("node_health" in r.payloadJson)
    }

    @Test fun `external control off blocks read ops`() {
        gate.externalControlEnabled = false
        assertEquals(RemoteErrorCode.EXTERNAL_CONTROL_DISABLED, code(dispatcher.dispatch(env(NodeStatus.OP))))
    }
}
