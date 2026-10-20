package ai.hackerai.companion.ui

import ai.hackerai.companion.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp

/** One manual section: a heading and its body paragraphs. */
data class ManualSection(val title: String, val body: List<String>)

/**
 * Condensed in-app copy of docs/MANUAL.md. Keep the two in sync.
 * Tags: [Likely] = controller/fleet claim not verifiable in this repo;
 * [Roadmap] = not implemented.
 */
val MANUAL_SECTIONS: List<ManualSection> = listOf(
    ManualSection(
        "1. What this app is",
        listOf(
            "HackerAI Companion exposes security agent tasks, a skill catalog and finding " +
                "validation to the DroidCommand AI controller (ai.droidcommand.app) over an " +
                "AIDL bound service.",
            "Fleet role: BOUND_COMPANION — bound to the controller rather than standalone. " +
                "[Likely] fleet taxonomy, not a token in this app's code.",
            "If DroidCommand AI is not installed, the app shows a waiting screen and all " +
                "service calls return an error.",
        ),
    ),
    ManualSection(
        "2. Install & setup",
        listOf(
            "1. Install DroidCommand AI first.",
            "2. Install this APK (GitHub Release, CI artifact, or ./gradlew :app:assembleDebug).",
            "3. Companion and controller must be signed with the same key (debug with debug, " +
                "release with release). [Likely] the bind permission is signature-level.",
            "4. Open the app; Status should read \"Connected to DroidCommand AI\".",
            "5. Add an LLM API key in Settings.",
            "6. Optional: set up Shizuku for privileged commands.",
        ),
    ),
    ManualSection(
        "3. Features",
        listOf(
            "Status — DCA + health strip (LLM key present, Shizuku, external control, " +
                "pending approvals, protocol 1.1). No keys or chat shown.",
            "Skills — browse the built-in skill catalog; type in the search box to filter by " +
                "name, category or description.",
            "Settings — API key (encrypted, last 4 shown), Model, Endpoint (Anthropic Messages " +
                "format only — stock Ollama/LM Studio OpenAI URLs will reject). Toggles: " +
                "Require confirm for agent tasks (default ON); Allow external control (default OFF; " +
                "import never enables it).",
            "Approve — pending AIDL agent tasks and remote agent.run / finding.validate / " +
                "task.cancel waiting for on-device confirm. shizuku.shell is never listed.",
            "Shizuku — status card (not installed / not running / permission not granted / " +
                "ready). When ready: type a command, tap Run (with confirmation), review the exact " +
                "command in the dialog, tap Run or Cancel. Output shows exit code, stdout and " +
                "stderr; 30 s timeout.",
            "Help — this screen.",
            "Controller AIDL: runAgentTask (confirm-gated), getTaskResult, cancelTask, " +
                "getSkillCatalog, validateFinding, healthCheck, executeEnvelope (fleet v1).",
            "[Roadmap] Notebook mode — NOT implemented.",
        ),
    ),
    ManualSection(
        "4. Permissions",
        listOf(
            "INTERNET — call the LLM endpoint you configured.",
            "ai.droidcommand.permission.BIND_HACKERAI — fleet permission; also guards the bound " +
                "service so only the controller can bind. [Likely] signature-level.",
            "moe.shizuku.manager.permission.API_V23 — request privileged access from Shizuku, " +
                "only when you tap Grant access.",
            "Every privileged command needs your explicit confirmation in a dialog showing the " +
                "exact command; nothing auto-runs. The privileged shell is not exposed to the " +
                "controller over AIDL.",
        ),
    ),
    ManualSection(
        "5. Fleet integration",
        listOf(
            "External control is OFF by default (Settings). Only BIND_HACKERAI holders can bind, " +
                "calls are refused without DroidCommand AI, and no AIDL method runs shell or " +
                "privileged commands.",
            "Once bound, the controller can run LLM agent tasks using your configured key.",
            "[Likely] Controller-side gates (default-off remote control, per-action approval) " +
                "live in the droidcommand-AI repo and are not verified here.",
        ),
    ),
    ManualSection(
        "6. Troubleshooting",
        listOf(
            "Stuck on \"Waiting for DroidCommand AI...\" — install it, then return to the app.",
            "Controller can't bind — signing mismatch; use matching debug/release builds.",
            "healthCheck says no_llm_provider — add an API key in Settings.",
            "LLM API error 401/403 — bad key; 4xx/404 — check endpoint and model ID.",
            "Shizuku not running after reboot — restart it via ADB / wireless debugging, tap Refresh.",
            "Output says (timed out) — command exceeded 30 s.",
        ),
    ),
    ManualSection(
        "7. Privacy & data",
        listOf(
            "API key, model and endpoint are stored in EncryptedSharedPreferences (Android " +
                "Keystore). App backup is disabled.",
            "Only the agent prompt and task text are sent, to the endpoint you configured. No " +
                "analytics in this app.",
            "Task results are kept in memory only (max 200). Shizuku output is not saved.",
            "Clear Key or uninstall to remove stored credentials.",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen() {
    val brand = colorResource(R.color.brand_primary)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Help & Manual") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(MANUAL_SECTIONS, key = { it.title }) { section ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium, color = brand)
                        section.body.forEach { para ->
                            Text(para, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                Text(
                    "Full manual: docs/MANUAL.md in the HackerAI-Commander repository.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
