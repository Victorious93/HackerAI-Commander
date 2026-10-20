package ai.hackerai.companion.ui

import ai.hackerai.companion.PendingAgentTask
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(
    pending: List<PendingAgentTask>,
    onApprove: (String) -> Unit,
    onDeny: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Approvals") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Text(
                text = "Agent tasks from the controller that need your confirm before using the saved API key. Remote agent.run is not listed here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRefresh) { Text("Refresh") }
            Spacer(Modifier.height(8.dp))
            if (pending.isEmpty()) {
                Text("No pending approvals.", style = MaterialTheme.typography.bodyLarge)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pending, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.taskPreview, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "id ${item.id.take(8)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onApprove(item.id) }) { Text("Approve") }
                                    Button(
                                        onClick = { onDeny(item.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        ),
                                    ) { Text("Deny") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
