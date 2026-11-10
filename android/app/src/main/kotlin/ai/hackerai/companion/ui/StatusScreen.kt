package ai.hackerai.companion.ui

import ai.hackerai.companion.CompanionStatus
import ai.hackerai.companion.SetupStep
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusScreen(
    isDcaConnected: Boolean,
    status: CompanionStatus? = null,
    onAddApiKey: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenShizuku: () -> Unit = {},
    onOpenApprovals: () -> Unit = {},
) {
    val resolved = status ?: CompanionStatus(
        dcaInstalled = isDcaConnected,
        llmConfigured = false,
        shizukuState = ai.hackerai.companion.shizuku.ShizukuState.NOT_INSTALLED,
        externalControlEnabled = false,
        pendingApprovals = 0,
        health = if (isDcaConnected) "no_llm_provider" else "dca_not_installed",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text("HackerAI Companion", style = MaterialTheme.typography.headlineMedium)
        Text(
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            text = "This is a companion for DroidCommand AI, not a standalone chat app. " +
                "Finish the steps below so the controller can use it.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Next steps", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        resolved.setupSteps().forEach { step ->
            SetupStepCard(
                step = step,
                onAction = {
                    when (step.id) {
                        "key", "external" -> if (step.id == "key" && !step.done) onAddApiKey() else onOpenSettings()
                        "shizuku" -> onOpenShizuku()
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
        if (resolved.pendingApprovals > 0) {
            OutlinedButton(onClick = onOpenApprovals, modifier = Modifier.fillMaxWidth()) {
                Text("Review ${resolved.pendingApprovals} pending approval(s)")
            }
            Spacer(Modifier.height(16.dp))
        }
        Text("Status details", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        resolved.rows().forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = "No API keys or chat content are shown here.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SetupStepCard(step: SetupStep, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = buildString {
                    append(if (step.done) "Done" else if (step.optional) "Optional" else "Needed")
                    append(" · ")
                    append(step.title)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step.actionLabel != null) {
                Button(onClick = onAction) { Text(step.actionLabel) }
            }
        }
    }
}
