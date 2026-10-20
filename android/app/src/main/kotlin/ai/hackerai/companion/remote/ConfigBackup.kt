package ai.hackerai.companion.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Safe, non-secret companion settings: the only thing `config.export` / `config.import`
 * ever touch. The API key, chat/task content and the external-control switch are not fields
 * here, so they cannot be exported or restored.
 */
@Serializable
data class CompanionConfig(val model: String, val endpoint: String)

@Serializable
data class UnifiedBackupEntry(
    val packageName: String,
    val schemaVersion: Int,
    val ciphertext: String? = null,
    val clearPayload: String? = null,
)

@Serializable
data class UnifiedFleetBackup(
    val version: Int,
    val createdAtMs: Long,
    val sourceNodeRef: String,
    val wrapping: String,
    val entries: List<UnifiedBackupEntry>,
)

/** `config.export` / `config.import` per droidcommand-AI docs/specs/UNIFIED_BACKUP.md (FROZEN). */
object ConfigBackup {
    const val OP_EXPORT = "config.export"
    const val OP_IMPORT = "config.import"
    const val SCHEMA_VERSION = 1
    const val BUNDLE_VERSION = 1
    const val WRAPPING_NONE = "none"
    const val WRAPPING_RAW = "raw-aes-256-gcm"
    const val WRAPPING_KEYSTORE = "android-keystore"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** Case-insensitive forbidden substrings (spec section 3 "Never in a payload"). */
    private val FORBIDDEN = listOf(
        "apikey", "api_key", "api-key", "apitoken", "api_token", "token", "bearer", "password",
        "passwd", "secret", "privatekey", "private_key", "signingkey", "keystore", "pem",
        "cookie", "codeword", "whitelist", "twilio", "authtoken", "numberpool",
    )

    /** Chat/content key names: refused as keys (values like ".../v1/messages" are fine). */
    private val CONTENT_KEYS = listOf("chat", "message", "prompt", "history", "transcript", "result")

    fun containsForbidden(payload: String): Boolean {
        val keys = runCatching { json.parseToJsonElement(payload).jsonObject.keys }.getOrNull() ?: return true
        val lower = payload.lowercase()
        return keys.any { k -> (FORBIDDEN + CONTENT_KEYS).any { it in k.lowercase() } } || FORBIDDEN.any { it in lower }
    }

    /** Endpoints with userinfo or a query string may carry credentials: refuse them. */
    private fun endpointIsSafe(endpoint: String): Boolean = runCatching {
        val u = URI(endpoint)
        u.scheme in setOf("https", "http") && u.userInfo == null && u.rawQuery == null && u.rawFragment == null
    }.getOrDefault(false)

    private fun aad(packageName: String, schemaVersion: Int, sourceNodeRef: String) =
        "$packageName|$schemaVersion|$sourceNodeRef".toByteArray()

    private val b64e = Base64.getUrlEncoder().withoutPadding()
    private val b64d = Base64.getUrlDecoder()

    fun encrypt(key: SecretKey, plain: String, aad: ByteArray): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        c.updateAAD(aad)
        return "v1.${b64e.encodeToString(iv)}.${b64e.encodeToString(c.doFinal(plain.toByteArray()))}"
    }

    fun decrypt(key: SecretKey, record: String, aad: ByteArray): String? = runCatching {
        val p = record.split(".")
        require(p.size == 3 && p[0] == "v1")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b64d.decode(p[1])))
        c.updateAAD(aad)
        String(c.doFinal(b64d.decode(p[2])))
    }.getOrNull()

    private fun result(cmd: BoundCompanionCommand, status: Int, code: String, msg: String, payload: String = "{}") =
        BoundCompanionResult(cmd.requestId, status, code, msg, payload)

    private fun invalid(cmd: BoundCompanionCommand, msg: String) =
        result(cmd, BoundCompanionResult.STATUS_INVALID, RemoteErrorCode.INVALID_PARAMETERS, msg)

    private fun args(cmd: BoundCompanionCommand): Map<String, String> =
        json.parseToJsonElement(cmd.argsJson).jsonObject.mapValues { it.value.jsonPrimitive.content }

    /**
     * `config.export`: one [UnifiedBackupEntry] (this node), as a one-entry bundle in `payloadJson`.
     * Clear payload only when `args.clearPayload == "true"` (local-dev); otherwise AES-GCM with
     * [key]. With no key, export refuses rather than falling back to clear text.
     */
    fun export(
        gate: RemoteCommandGate, caller: CallerIdentity, sourceNodeRef: String, cmd: BoundCompanionCommand,
        current: CompanionConfig, nowMs: Long, key: SecretKey?,
    ): BoundCompanionResult {
        if (cmd.command != OP_EXPORT) return result(cmd, BoundCompanionResult.STATUS_UNSUPPORTED, RemoteErrorCode.UNSUPPORTED_OP, "Not $OP_EXPORT")
        val verdict = gate.evaluate(caller, sourceNodeRef, cmd)
        if (verdict.status != BoundCompanionResult.STATUS_OK) return verdict
        val a = args(cmd)
        a["packageName"]?.takeIf { it.isNotBlank() && it != NodeStatus.PACKAGE_NAME }?.let {
            return result(cmd, BoundCompanionResult.STATUS_DENIED, RemoteErrorCode.NOT_FOUND, "No entry for $it")
        }
        if (!endpointIsSafe(current.endpoint)) return invalid(cmd, "Endpoint may carry credentials; refusing export")
        val plain = json.encodeToString(current)
        if (containsForbidden(plain)) return invalid(cmd, "Payload contains a forbidden name")
        val clear = a["clearPayload"] == "true"
        val exporter = cmd.targetNodeRef
        val entry = if (clear) {
            UnifiedBackupEntry(NodeStatus.PACKAGE_NAME, SCHEMA_VERSION, clearPayload = plain)
        } else {
            key ?: return invalid(cmd, "No wrapping key; encrypted export unavailable")
            UnifiedBackupEntry(NodeStatus.PACKAGE_NAME, SCHEMA_VERSION,
                ciphertext = encrypt(key, plain, aad(NodeStatus.PACKAGE_NAME, SCHEMA_VERSION, exporter)))
        }
        val bundle = UnifiedFleetBackup(BUNDLE_VERSION, nowMs, exporter, if (clear) WRAPPING_NONE else WRAPPING_RAW, listOf(entry))
        return result(cmd, BoundCompanionResult.STATUS_OK, "ok", "Exported", json.encodeToString(bundle))
    }

    /**
     * `config.import` (spec section 4 order). [apply] is called only on a confirmed, non-dry-run
     * import with policy `overwrite`. After any import the gate's external control is forced
     * OFF, so remote.command is never enabled by a restore.
     *
     * [Likely] HackerAI's payload has no id-keyed lists, so the whole config is treated as one
     * row: `skip` (default) and `merge-by-id` keep the live config; `overwrite` replaces it.
     */
    fun import(
        gate: RemoteCommandGate, caller: CallerIdentity, sourceNodeRef: String, cmd: BoundCompanionCommand,
        current: CompanionConfig, key: SecretKey?, confirmed: Boolean, apply: (CompanionConfig) -> Unit,
    ): BoundCompanionResult {
        if (cmd.command != OP_IMPORT) return result(cmd, BoundCompanionResult.STATUS_UNSUPPORTED, RemoteErrorCode.UNSUPPORTED_OP, "Not $OP_IMPORT")
        val a = runCatching { args(cmd) }.getOrElse { return invalid(cmd, "Malformed args") }
        val dryRun = a["dryRun"] == "true"
        val verdict = gate.evaluate(caller, sourceNodeRef, cmd, confirmed = confirmed, dryRun = dryRun)
        if (verdict.status != BoundCompanionResult.STATUS_OK) return verdict
        try {
            val policy = a["conflictPolicy"] ?: "skip"
            if (policy !in setOf("skip", "overwrite", "merge-by-id")) return invalid(cmd, "Unknown conflictPolicy")
            val bundle = runCatching { json.decodeFromString<UnifiedFleetBackup>(a["bundle"] ?: "") }
                .getOrElse { return invalid(cmd, "Bad bundle JSON") }
            if (bundle.version != BUNDLE_VERSION) return invalid(cmd, "Unknown bundle version")
            if (bundle.wrapping !in setOf(WRAPPING_NONE, WRAPPING_RAW, WRAPPING_KEYSTORE)) return invalid(cmd, "Unknown wrapping")
            if (bundle.entries.map { it.packageName }.toSet().size != bundle.entries.size) return invalid(cmd, "Duplicate packageName")
            if (bundle.entries.any { (it.ciphertext == null) == (it.clearPayload == null) }) return invalid(cmd, "Need exactly one of ciphertext/clearPayload")
            if (bundle.entries.any { (bundle.wrapping == WRAPPING_NONE) != (it.clearPayload != null) }) return invalid(cmd, "Wrapping/payload mismatch")
            val entry = bundle.entries.firstOrNull { it.packageName == NodeStatus.PACKAGE_NAME }
                ?: return result(cmd, BoundCompanionResult.STATUS_DENIED, RemoteErrorCode.NOT_FOUND, "No ${NodeStatus.PACKAGE_NAME} entry")
            val plain = entry.clearPayload ?: run {
                if (bundle.wrapping != WRAPPING_RAW || key == null) return invalid(cmd, "No key for ${bundle.wrapping}")
                decrypt(key, entry.ciphertext!!, aad(entry.packageName, entry.schemaVersion, bundle.sourceNodeRef))
                    ?: return result(cmd, BoundCompanionResult.STATUS_DENIED, RemoteErrorCode.INTERNAL, "Decrypt failed")
            }
            if (containsForbidden(plain)) return invalid(cmd, "Payload contains a forbidden name")
            val incoming = runCatching { json.decodeFromString<CompanionConfig>(plain) }.getOrElse { return invalid(cmd, "Bad payload") }
            if (!endpointIsSafe(incoming.endpoint)) return invalid(cmd, "Endpoint may carry credentials")
            val next = if (policy == "overwrite") incoming else current
            val report = """{"dryRun":$dryRun,"conflictPolicy":"$policy","wouldChange":${next != current}}"""
            if (!dryRun && next != current) apply(next)
            return result(cmd, BoundCompanionResult.STATUS_OK, "ok", if (dryRun) "Dry run" else "Imported", report)
        } finally {
            // Spec step 7: never leave remote.command enabled after an import.
            if (!dryRun) gate.externalControlEnabled = false
        }
    }
}
