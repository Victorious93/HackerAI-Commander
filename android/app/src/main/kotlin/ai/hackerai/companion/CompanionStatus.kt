package ai.hackerai.companion

import ai.hackerai.companion.remote.BRIDGE_PROTOCOL_MAJOR
import ai.hackerai.companion.remote.BRIDGE_PROTOCOL_MINOR
import ai.hackerai.companion.shizuku.ShizukuState

/** One setup checklist item. Never carries API keys or chat. */
data class SetupStep(
    val id: String,
    val title: String,
    val detail: String,
    val done: Boolean,
    val optional: Boolean,
    val actionLabel: String?,
)

/** Display-only health + setup checklist for StatusScreen. Never carries API keys or chat. */
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
        "DroidCommand AI" to if (dcaInstalled) "installed (not a bind check)" else "missing",
        "LLM provider" to if (llmConfigured) "key configured" else "no key",
        "Shizuku" to shizukuState.name.lowercase().replace('_', ' '),
        "External control" to if (externalControlEnabled) "ON" else "OFF (default)",
        "Pending approvals" to pendingApprovals.toString(),
        "Health" to health,
        "Bridge protocol" to "$protocolMajor.$protocolMinor",
    )

    fun setupSteps(): List<SetupStep> = listOf(
        SetupStep(
            id = "dca",
            title = if (dcaInstalled) "DroidCommand AI is installed" else "Install DroidCommand AI",
            detail = if (dcaInstalled) {
                "The DroidCommand AI package is on this phone. That is not the same as bound: " +
                    "the controller may still be unable to connect (for example if the signing keys don't match)."
            } else {
                "This companion works with the DroidCommand AI controller. Install it, then return here."
            },
            done = dcaInstalled,
            optional = false,
            actionLabel = null,
        ),
        SetupStep(
            id = "key",
            title = if (llmConfigured) "API key saved" else "Add an API key",
            detail = "Agent tasks need a key in Settings. The key is never shown on this screen.",
            done = llmConfigured,
            optional = false,
            actionLabel = if (llmConfigured) "Open Settings" else "Add API key",
        ),
        SetupStep(
            id = "shizuku",
            title = if (shizukuState == ShizukuState.GRANTED) {
                "Shizuku is ready"
            } else {
                "Optional: set up Shizuku"
            },
            detail = "Needed only for privileged shell commands on this phone.",
            done = shizukuState == ShizukuState.GRANTED,
            optional = true,
            actionLabel = "Open Shizuku",
        ),
        SetupStep(
            id = "external",
            title = if (externalControlEnabled) {
                "External control is ON"
            } else {
                "Optional: allow external control"
            },
            detail = "OFF by default. Turn on Allow external control in Settings if you want a " +
                "bound controller to send remote commands. Destructive ops still need on-device " +
                "confirm. This does not auto-run anything.",
            done = externalControlEnabled,
            optional = true,
            actionLabel = "Open Settings",
        ),
    )
}
