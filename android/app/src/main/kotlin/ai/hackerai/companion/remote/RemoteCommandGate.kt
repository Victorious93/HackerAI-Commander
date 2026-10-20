package ai.hackerai.companion.remote

import java.util.concurrent.ConcurrentHashMap

/**
 * Companion-side trust pipeline for envelope v1, in spec order:
 * identity -> external-control switch (OFF by default) -> replay/ttl -> local policy.
 *
 * Local policy: every HackerAI pentest / privileged action (agent tasks, finding validation,
 * Shizuku shell) is classified DESTRUCTIVE here, independently of the advisory `destructive`
 * flag, and is never auto-run: it returns `confirmation_required`. Only read ops pass.
 * This gate does not execute anything; it only decides.
 */
class RemoteCommandGate(
    private val clock: () -> Long = System::currentTimeMillis,
    externalControlEnabled: Boolean = false,
) {
    @Volatile var externalControlEnabled: Boolean = externalControlEnabled

    /** Seen-nonce cache keyed by (sourceNodeRef, nonce); in-memory only. */
    private val seen = ConcurrentHashMap<Pair<String, String>, Long>()

    companion object {
        const val PERMISSION_BIND_HACKERAI = "ai.droidcommand.permission.BIND_HACKERAI"
        private const val RETENTION_MS = 600_000L

        /** Read-only ops that may be served remotely without confirmation. */
        val READ_OPS = setOf("capabilities.get", NodeStatus.OP, "skills.catalog", "task.result", "health.check")

        /** Pentest / privileged ops: always destructive, always gated. */
        val DESTRUCTIVE_OPS = setOf("agent.run", "finding.validate", "task.cancel", "shizuku.shell")
    }

    fun evaluate(caller: CallerIdentity, sourceNodeRef: String, cmd: BoundCompanionCommand): BoundCompanionResult {
        fun deny(status: Int, code: String, msg: String) =
            BoundCompanionResult(cmd.requestId, status, code, msg)

        if (!caller.holdsBindPermission) {
            return deny(BoundCompanionResult.STATUS_DENIED, RemoteErrorCode.UNAUTHORIZED, "Caller uid ${caller.uid} lacks $PERMISSION_BIND_HACKERAI")
        }
        if (!externalControlEnabled) {
            return deny(BoundCompanionResult.STATUS_DENIED, RemoteErrorCode.EXTERNAL_CONTROL_DISABLED, "External control is off")
        }
        if (cmd.requestId.isBlank() || cmd.command.isBlank() || cmd.nonce.isBlank() ||
            cmd.targetNodeRef.isBlank() || cmd.ttlMs <= 0 || cmd.ttlMs > BoundCompanionCommand.MAX_TTL_MS ||
            !isValidArgsJson(cmd.argsJson)
        ) {
            return deny(BoundCompanionResult.STATUS_INVALID, RemoteErrorCode.INVALID_PARAMETERS, "Malformed envelope")
        }
        val now = clock()
        if (now > cmd.timestampMs + cmd.ttlMs || cmd.timestampMs > now + BoundCompanionCommand.FUTURE_SKEW_MS) {
            return deny(BoundCompanionResult.STATUS_DENIED, RemoteErrorCode.EXPIRED, "Envelope expired")
        }
        seen.entries.removeIf { now - it.value > RETENTION_MS }
        if (seen.putIfAbsent(sourceNodeRef to cmd.nonce, now) != null) {
            return deny(BoundCompanionResult.STATUS_DENIED, RemoteErrorCode.REPLAY_DETECTED, "Nonce already seen")
        }
        return when (cmd.command) {
            in READ_OPS -> BoundCompanionResult(cmd.requestId, BoundCompanionResult.STATUS_OK, "ok", "Allowed")
            // The advisory `destructive` flag is ignored: classification is by command only.
            in DESTRUCTIVE_OPS -> deny(BoundCompanionResult.STATUS_DENIED, RemoteErrorCode.CONFIRMATION_REQUIRED, "Destructive op requires on-device approval")
            else -> deny(BoundCompanionResult.STATUS_UNSUPPORTED, RemoteErrorCode.UNSUPPORTED_OP, "Unknown op ${cmd.command}")
        }
    }
}
