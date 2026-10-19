package ai.hackerai.companion.ui

import ai.hackerai.companion.shizuku.ShellResult
import ai.hackerai.companion.shizuku.ShizukuManager
import ai.hackerai.companion.shizuku.ShizukuState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Status + privileged-console screen. Renders the four Shizuku states with
 * consistent copy and gates every privileged command behind an explicit
 * confirmation dialog (never auto-runs).
 */
@Composable
fun ShizukuScreen(
    stateFlow: State<ShizukuState>,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onExec: suspend (String) -> ShellResult,
) {
    val state by stateFlow
    val scope = rememberCoroutineScope()

    var command by remember { mutableStateOf("") }
    var pendingCommand by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Privileged access", style = MaterialTheme.typography.headlineSmall)

        StatusCard(state = state, onRequestPermission = onRequestPermission, onRefresh = onRefresh)

        val granted = state == ShizukuState.GRANTED
        Text(
            "Run a privileged command",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (granted) {
                "Commands run through Shizuku with elevated privileges. You will be " +
                    "asked to confirm before anything runs."
            } else {
                "Enable Shizuku above to run privileged commands."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Shell command") },
            enabled = granted && !running,
            singleLine = false,
        )
        Button(
            onClick = { if (command.isNotBlank()) pendingCommand = command },
            enabled = granted && !running && command.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (running) "Running…" else "Run (with confirmation)")
        }

        if (output.isNotEmpty()) {
            Text("Output", style = MaterialTheme.typography.titleMedium)
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        output,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }

    val toConfirm = pendingCommand
    if (toConfirm != null) {
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text("Run privileged command?") },
            text = {
                Column {
                    Text(
                        "This runs with Shizuku's elevated privileges on this device. " +
                            "Only run commands you understand and are authorized to run.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            toConfirm,
                            modifier = Modifier.padding(10.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCommand = null
                    running = true
                    output = ""
                    scope.launch {
                        val result = runCatching { onExec(toConfirm) }
                        output = result.fold(
                            onSuccess = { it.render() },
                            onFailure = { "Error: ${it.message}" },
                        )
                        running = false
                    }
                }) { Text("Run") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommand = null }) { Text("Cancel") }
            },
        )
    }
}

private fun ShellResult.render(): String = buildString {
    append("exit=").append(exitCode)
    if (timedOut) append(" (timed out)")
    append('\n')
    if (stdout.isNotEmpty()) append(stdout)
    if (stderr.isNotEmpty()) {
        if (stdout.isNotEmpty()) append('\n')
        append("[stderr]\n").append(stderr)
    }
}

@Composable
private fun StatusCard(
    state: ShizukuState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
) {
    val copy = state.copy()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = copy.color,
                    shape = CircleShape,
                    modifier = Modifier.size(12.dp).clip(CircleShape),
                ) {}
                Spacer(Modifier.size(10.dp))
                Text(copy.title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                copy.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state == ShizukuState.DENIED) {
                    Button(onClick = onRequestPermission) { Text("Grant access") }
                }
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
    }
}

private data class StatusCopy(val title: String, val detail: String, val color: Color)

private fun ShizukuState.copy(): StatusCopy = when (this) {
    ShizukuState.NOT_INSTALLED -> StatusCopy(
        title = "Shizuku not installed",
        detail = "Install Shizuku (or enable Sui) to allow HackerAI to run privileged " +
            "system commands you request.",
        color = Color(0xFF9AA0A6),
    )
    ShizukuState.NOT_RUNNING -> StatusCopy(
        title = "Shizuku not running",
        detail = "Shizuku is installed but its service isn't running. Start it in the " +
            "Shizuku app (ADB or root), then tap Refresh.",
        color = Color(0xFFF2B705),
    )
    ShizukuState.DENIED -> StatusCopy(
        title = "Permission not granted",
        detail = "Shizuku is running. Grant HackerAI access to enable privileged commands.",
        color = Color(0xFFEA4335),
    )
    ShizukuState.GRANTED -> StatusCopy(
        title = "Privileged access ready",
        detail = "HackerAI can run privileged commands through Shizuku. Each command still " +
            "requires your explicit confirmation.",
        color = Color(0xFF34D399),
    )
}
