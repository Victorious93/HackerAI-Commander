package ai.hackerai.companion

import ai.hackerai.companion.remote.BRIDGE_PROTOCOL_MAJOR
import ai.hackerai.companion.remote.BRIDGE_PROTOCOL_MINOR
import ai.hackerai.companion.shizuku.ShizukuState

/** Display-only health strip for StatusScreen. Never carries API keys or chat. */
data class CompanionStatus(
    val dcaInstalled: Boolean,
    val llmConfigured: Boolean,
    val shizukuState: ShizukuState,
    val externalControlEnabled: Boolean,
    val pendingApprovals: Int,
    val health: String,
    val protocolMajor: Int = BRIDGE_PROTOCOL_MAJOR,
    val protocolMinor: Int = BRIDGE_PROTOCOL_MINOR,
) {
    fun rows(): List<Pair<String, String>> = listOf(
        "DroidCommand AI" to if (dcaInstalled) "installed" else "missing",
        "LLM provider" to if (llmConfigured) "key configured" else "no key",
        "Shizuku" to shizukuState.name.lowercase().replace('_', ' '),
        "External control" to if (externalControlEnabled) "ON" else "OFF (default)",
        "Pending approvals" to pendingApprovals.toString(),
        "Health" to health,
        "Bridge protocol" to "$protocolMajor.$protocolMinor",
    )
}
