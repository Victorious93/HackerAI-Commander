package ai.hackerai.companion.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Fleet remote-control envelope v1 (droidcommand-AI docs/specs/REMOTE_CONTROL.md, FROZEN). */
const val BRIDGE_PROTOCOL_MAJOR = 1
const val BRIDGE_PROTOCOL_MINOR = 1

/**
 * BOUND_COMPANION encoding (spec Appendix B): the frozen flat fields mapped 1:1 onto the
 * existing companion `CommandEnvelope` field names. Same shape as the controller's
 * `BoundCompanionCommand`. This is a Kotlin record only: no `.aidl` file is changed and no
 * parcel field is added (the live AIDL bump is spec item 7.3, done together with the controller).
 *
 * | frozen field    | CommandEnvelope field |
 * |-----------------|-----------------------|
 * | requestId       | requestId             |
 * | actionRef       | command               |
 * | args            | argsJson              |
 * | nonce           | nonce                 |
 * | timestampMs     | timestampMs           |
 * | ttlMs           | ttlMs                 |
 * | destructive     | destructive (advisory only, never used to authorize) |
 * | targetNodeRef   | targetNodeRef (this companion's node id, hub-side)    |
 */
@Serializable
data class BoundCompanionCommand(
    val requestId: String,
    val command: String,
    val argsJson: String = "{}",
    val nonce: String,
    val timestampMs: Long,
    val ttlMs: Long = DEFAULT_TTL_MS,
    val destructive: Boolean = false,
    val targetNodeRef: String,
) {
    companion object {
        const val DEFAULT_TTL_MS = 120_000L
        const val MAX_TTL_MS = 300_000L
        const val FUTURE_SKEW_MS = 120_000L
    }
}

/**
 * Reply mapped onto the existing `ResultEnvelope` field names
 * (`requestId`, `status`, `code`, `message`, `payloadJson`). `code` uses the spec's
 * ratified error categories.
 */
@Serializable
data class BoundCompanionResult(
    val requestId: String,
    val status: Int,
    val code: String,
    val message: String,
    val payloadJson: String = "{}",
) {
    companion object {
        // Same values as ResultEnvelope.STATUS_* constants.
        const val STATUS_OK = 0
        const val STATUS_UNSUPPORTED = 2
        const val STATUS_DENIED = 3
        const val STATUS_INVALID = 4
    }
}

/** Ratified `RemoteErrorCategory` codes (spec section 3). */
object RemoteErrorCode {
    const val INVALID_PARAMETERS = "invalid_parameters"
    const val UNAUTHORIZED = "unauthorized"
    const val REPLAY_DETECTED = "replay_detected"
    const val EXPIRED = "expired"
    const val EXTERNAL_CONTROL_DISABLED = "external_control_disabled"
    const val CONFIRMATION_REQUIRED = "confirmation_required"
    const val UNSUPPORTED_OP = "unsupported_op"
    const val CAPABILITY_OFF = "capability_off"
    const val NOT_FOUND = "not_found"
    const val INTERNAL = "internal"
}

/**
 * Caller identity for authorization: Binder caller uid plus whether that caller holds the
 * `ai.droidcommand.permission.BIND_HACKERAI` signature permission. Never read from the payload.
 */
data class CallerIdentity(val uid: Int, val holdsBindPermission: Boolean)

internal fun isValidArgsJson(argsJson: String): Boolean =
    runCatching { Json.parseToJsonElement(argsJson) is JsonObject }.getOrDefault(false)

/** `capabilities.get` per-op descriptor. */
@Serializable
data class OpDescriptor(val name: String, val kind: String, val remoteAllowed: Boolean, val requiresConfirm: Boolean)
