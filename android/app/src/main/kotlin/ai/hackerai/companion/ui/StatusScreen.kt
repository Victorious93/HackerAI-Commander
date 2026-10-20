package ai.hackerai.companion.ui

import ai.hackerai.companion.CompanionStatus
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatusScreen(
    isDcaConnected: Boolean,
    status: CompanionStatus? = null,
) {
    val rows = status?.rows() ?: listOf(
        "DroidCommand AI" to if (isDcaConnected) "installed" else "missing",
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
            text = if (isDcaConnected) "Connected to DroidCommand AI" else "Waiting for DroidCommand AI…",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Status", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        rows.forEach { (label, value) ->
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
