package ai.hackerai.companion.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Frozen `NodeStatusSnapshot` wire object (droidcommand-AI docs/specs/STATUS_TRAY.md §2). */
@Serializable
data class NodeStatusSnapshot(
    val nodeId: String,
    val packageName: String?,
    val connectionClass: String,
    val updatedAtMs: Long,
    val domains: List<StatusTrayDomainStatus>,
)

@Serializable
data class StatusTrayDomainStatus(val name: String, val state: String, val detail: String? = null)

/**
 * `node.status.get` {kind:read, remoteAllowed:true, requiresConfirm:false}.
 * Builds the snapshot from booleans only: it never takes the API key, prompts, or task
 * results as input, so it cannot leak them. Callers must pass the gate first.
 */
object NodeStatus {
    const val OP = "node.status.get"
    const val PACKAGE_NAME = "ai.hackerai.companion"
    const val CONNECTION_CLASS = "BOUND_COMPANION"
    private const val MAX_DETAIL = 200
    private val FORBIDDEN = listOf(
        "password", "apikey", "api_key", "api_token", "token", "secret", "codeword",
        "whitelist", "signingkey", "privatekey", "cookie",
    )
    private val json = Json { encodeDefaults = false }

    fun build(nodeId: String, nowMs: Long, dcaInstalled: Boolean, llmConfigured: Boolean): NodeStatusSnapshot {
        val health = when {
            !dcaInstalled -> StatusTrayDomainStatus("node_health", "down", "controller not installed")
            !llmConfigured -> StatusTrayDomainStatus("node_health", "degraded", "no llm provider")
            else -> StatusTrayDomainStatus("node_health", "ok")
        }
        return NodeStatusSnapshot(nodeId, PACKAGE_NAME, CONNECTION_CLASS, nowMs, listOf(health))
    }

    /** Spec §6: refuse (invalid_parameters) rather than strip a detail that names a secret. */
    fun encode(snapshot: NodeStatusSnapshot): String? {
        val bad = snapshot.nodeId.isBlank() || snapshot.domains.any { d ->
            val t = d.detail?.lowercase()?.replace(" ", "") ?: return@any false
            t.length > MAX_DETAIL || FORBIDDEN.any { it in t }
        }
        return if (bad) null else json.encodeToString(snapshot)
    }

    /** Runs the gate, then answers with the snapshot as `payloadJson`. */
    fun handle(
        gate: RemoteCommandGate, caller: CallerIdentity, sourceNodeRef: String, cmd: BoundCompanionCommand,
        nowMs: Long, dcaInstalled: Boolean, llmConfigured: Boolean,
    ): BoundCompanionResult {
        if (cmd.command != OP) {
            return BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_UNSUPPORTED, RemoteErrorCode.UNSUPPORTED_OP, "Not $OP")
        }
        val verdict = gate.evaluate(caller, sourceNodeRef, cmd)
        if (verdict.status != BoundCompanionResult.STATUS_OK) return verdict
        // [Likely] nodeId: the controller addresses this companion by targetNodeRef.
        val payload = encode(build(cmd.targetNodeRef, nowMs, dcaInstalled, llmConfigured))
            ?: return BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_INVALID, RemoteErrorCode.INVALID_PARAMETERS, "Snapshot refused")
        return BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_OK, "ok", "Allowed", payload)
    }
}
