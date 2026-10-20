package ai.hackerai.companion.ui

import ai.hackerai.companion.llm.HttpLocalLlmProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentApiKey: String?,
    currentModel: String,
    currentEndpoint: String,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onSaveModel: (String) -> Unit,
    onSaveEndpoint: (String) -> Unit,
    requireAgentTaskConfirm: Boolean = true,
    onRequireAgentTaskConfirmChange: (Boolean) -> Unit = {},
) {
    var apiKeyDraft by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf(currentModel) }
    var endpointDraft by remember { mutableStateOf(currentEndpoint) }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("API Key", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (currentApiKey != null) {
                Text(
                    text = "Key configured (ends in …${currentApiKey.takeLast(4)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onClearApiKey,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Clear Key")
                }
            } else {
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text("Enter API key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Text(
                            text = if (showApiKey) "Hide" else "Show",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (apiKeyDraft.isNotBlank()) {
                            onSaveApiKey(apiKeyDraft)
                            apiKeyDraft = ""
                        }
                    },
                    enabled = apiKeyDraft.isNotBlank(),
                ) {
                    Text("Save Key")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Agent tasks", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "When on (default), starting an agent task that would use the saved API key needs an on-device confirm. Remote agent.run is always blocked by the fleet gate and never auto-runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Require confirm for agent tasks",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Switch(
                    checked = requireAgentTaskConfirm,
                    onCheckedChange = onRequireAgentTaskConfirmChange,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("Model", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = modelDraft,
                onValueChange = { modelDraft = it },
                label = { Text("Model ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(HttpLocalLlmProvider.DEFAULT_MODEL) },
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSaveModel(modelDraft) },
                enabled = modelDraft.isNotBlank() && modelDraft != currentModel,
            ) {
                Text("Save Model")
            }

            Spacer(Modifier.height(24.dp))
            Text("Endpoint", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Override to point at a local OpenAI-compatible server (e.g. Ollama, LM Studio).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = endpointDraft,
                onValueChange = { endpointDraft = it },
                label = { Text("Endpoint URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(HttpLocalLlmProvider.DEFAULT_ENDPOINT) },
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSaveEndpoint(endpointDraft) },
                enabled = endpointDraft.isNotBlank() && endpointDraft != currentEndpoint,
            ) {
                Text("Save Endpoint")
            }
        }
    }
}
