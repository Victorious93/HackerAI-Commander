package ai.hackerai.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Copy for the first-launch "DroidCommand AI missing" screen. Not a theme or key surface. */
object DcaSetup {
    const val RELEASES_URI = "https://github.com/Victorious93/droidcommand-AI/releases"
    const val TITLE = "HackerAI Companion"
    const val INTRO =
        "This app is the HackerAI companion. It works with DroidCommand AI, a separate " +
            "controller app you install on this phone."
    const val MISSING =
        "DroidCommand AI doesn't appear to be installed, or this app can't see it yet."
    const val HOW_TO_GET =
        "Get DroidCommand AI from the GitHub Releases page for Victorious93/droidcommand-AI. " +
            "Use a build signed with the same key as this companion (debug with debug, " +
            "release with release)."
    const val GET_BUTTON = "Get DroidCommand AI"
    const val REFRESH_BUTTON = "I installed it — Refresh"
    const val HELP_BUTTON = "Help"
    const val BACK_BUTTON = "Back"
}

/**
 * Shown when [ai.hackerai.companion.DependencyGuard.isDcaInstalled] is false.
 * Does not enable external control or change any safety toggle.
 */
@Composable
fun DcaRequiredScreen(
    onRefresh: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    var showHelp by remember { mutableStateOf(false) }
    if (showHelp) {
        Column(modifier = Modifier.fillMaxSize()) {
            TextButton(
                onClick = { showHelp = false },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text(DcaSetup.BACK_BUTTON) }
            HelpScreen()
        }
    } else {
        WaitingForDcaScreen(
            onRefresh = onRefresh,
            onOpenReleases = onOpenReleases,
            onOpenHelp = { showHelp = true },
        )
    }
}

@Composable
fun WaitingForDcaScreen(
    onRefresh: () -> Unit,
    onOpenReleases: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(DcaSetup.TITLE, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(DcaSetup.INTRO, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text(DcaSetup.MISSING, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            DcaSetup.HOW_TO_GET,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth()) {
            Text(DcaSetup.GET_BUTTON)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text(DcaSetup.REFRESH_BUTTON)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) {
            Text(DcaSetup.HELP_BUTTON)
        }
    }
}
