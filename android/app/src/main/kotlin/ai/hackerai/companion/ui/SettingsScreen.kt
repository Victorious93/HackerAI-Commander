package ai.hackerai.companion.ui

import ai.hackerai.companion.llm.HttpLocalLlmProvider
import ai.hackerai.companion.llm.LlmConnectionResult
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
    form: SettingsFormState,
    onTestConnection: suspend () -> LlmConnectionResult = {
        LlmConnectionResult(false, null, "Test connection is not available.")
    },
    requireAgentTaskConfirm: Boolean = true,
    onRequireAgentTaskConfirmChange: (Boolean) -> Unit = {},
    externalControlEnabled: Boolean = false,
    onExternalControlEnabledChange: (Boolean) -> Unit = {},
) {
    var apiKeyDraft by remember { mutableStateOf("") }
    var modelDraft by remember { mutableStateOf(form.model) }
    var endpointDraft by remember { mutableStateOf(form.endpoint) }
    var showApiKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<LlmConnectionResult?>(null) }
    val scope = rememberCoroutineScope()
    val currentApiKey = form.apiKey
    val currentModel = form.model
    val currentEndpoint = form.endpoint

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
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
                    onClick = { form.clearApiKey(); testResult = null },
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
                        TextButton(onClick = { showApiKey = !showApiKey }) {
                            Text(
                                text = if (showApiKey) "Hide" else "Show",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        form.saveApiKey(apiKeyDraft)
                        apiKeyDraft = ""
                        testResult = null
                    },
                    enabled = apiKeyDraft.isNotBlank(),
                ) {
                    Text("Save Key")
                }
            }

            if (currentApiKey != null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        testing = true
                        testResult = null
                        scope.launch {
                            testResult = withContext(Dispatchers.IO) { onTestConnection() }
                            testing = false
                        }
                    },
                    enabled = !testing,
                ) {
                    Text(if (testing) "Testing…" else "Test connection")
                }
            }
            testResult?.let { result ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.ok) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.error,
                )
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
            Text("Fleet remote control", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "OFF by default. When on, a bound controller holding BIND_HACKERAI may call executeEnvelope ops (status, catalog, …). Destructive ops still need on-device confirm. Config import never turns this on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Allow external control",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Switch(
                    checked = externalControlEnabled,
                    onCheckedChange = onExternalControlEnabledChange,
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
                onClick = { form.saveModel(modelDraft) },
                enabled = modelDraft.isNotBlank() && modelDraft != currentModel,
            ) {
                Text("Save Model")
            }

            Spacer(Modifier.height(24.dp))
            Text("Endpoint", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Requests always use the Anthropic Messages format (x-api-key + anthropic-version headers, /v1/messages body). Stock Ollama and LM Studio OpenAI endpoints will reject them. Use the Anthropic API, or a proxy that speaks Anthropic Messages (for example LiteLLM / an Anthropic-compatible gateway) in front of a local model.",
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
                onClick = { form.saveEndpoint(endpointDraft) },
                enabled = endpointDraft.isNotBlank() && endpointDraft != currentEndpoint,
            ) {
                Text("Save Endpoint")
            }
        }
    }
}
