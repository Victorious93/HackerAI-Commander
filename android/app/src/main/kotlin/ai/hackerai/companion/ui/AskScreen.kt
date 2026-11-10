package ai.hackerai.companion.ui

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    hasApiKey: Boolean,
    onOpenSettings: () -> Unit,
    onRun: (inputJson: String) -> String,
    onPoll: (taskId: String) -> String,
    onCancel: (taskId: String) -> Unit,
) {
    var prompt by remember { mutableStateOf("") }
    var view by remember { mutableStateOf<OnDeviceAsk.TaskView?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text(OnDeviceAsk.TITLE) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!hasApiKey) {
                Text(OnDeviceAsk.NEEDS_KEY, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Add API key")
                }
            } else {
                Text(OnDeviceAsk.CONFIRM_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ask or describe a task") },
                    enabled = view?.inProgress != true,
                    minLines = 4,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            when (val decision = OnDeviceAsk.decide(prompt, hasApiKey = true)) {
                                OnDeviceAsk.Decision.EmptyPrompt -> {
                                    view = OnDeviceAsk.TaskView("error", "Enter a task first.", inProgress = false)
                                }
                                OnDeviceAsk.Decision.NeedsApiKey -> onOpenSettings()
                                is OnDeviceAsk.Decision.Run -> {
                                    view = OnDeviceAsk.TaskView("queued", "Running…", inProgress = true)
                                    scope.launch {
                                        val started = withContext(Dispatchers.IO) { OnDeviceAsk.parseStart(onRun(decision.inputJson)) }
                                        view = started
                                        val id = started.taskId ?: return@launch
                                        while (isActive && view?.inProgress == true) {
                                            delay(500)
                                            val polled = withContext(Dispatchers.IO) { OnDeviceAsk.parsePoll(onPoll(id), id) }
                                            view = polled
                                            if (!polled.inProgress) break
                                        }
                                    }
                                }
                            }
                        },
                        enabled = view?.inProgress != true && prompt.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text(OnDeviceAsk.RUN_BUTTON) }
                    if (view?.inProgress == true && view?.taskId != null) {
                        OutlinedButton(onClick = { view?.taskId?.let(onCancel) }) { Text("Cancel") }
                    }
                }
            }
            view?.let { result ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Status: ${result.status}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(result.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
