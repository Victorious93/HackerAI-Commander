package ai.hackerai.companion

import ai.hackerai.companion.llm.HttpLocalLlmProvider
import ai.hackerai.companion.shizuku.ShizukuState
import ai.hackerai.companion.remote.RemoteCommandGate
import ai.hackerai.companion.service.AgentTaskRunner
import ai.hackerai.companion.shizuku.ShizukuManager
import ai.hackerai.companion.shizuku.ShizukuState
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ai.hackerai.companion.ui.theme.HackerAITheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var dependencyGuard: DependencyGuard
    @Inject lateinit var llmProvider: HttpLocalLlmProvider
    @Inject lateinit var shizukuManager: ShizukuManager
    @Inject lateinit var agentTaskConfirmPolicy: AgentTaskConfirmPolicy
    @Inject lateinit var externalControlPrefs: ExternalControlPrefs
    @Inject lateinit var remoteCommandGate: RemoteCommandGate
    @Inject lateinit var pendingAgentApprovals: PendingAgentApprovals
    @Inject lateinit var agentTaskRunner: AgentTaskRunner

    private var dcaInstalledAtCreate: Boolean = false
    private var pendingApprovals by mutableStateOf(emptyList<PendingAgentTask>())

    private fun refreshApprovals() {
        pendingApprovals = pendingAgentApprovals.list()
    }

    private fun approvePending(id: String) {
        val item = pendingAgentApprovals.take(id) ?: return refreshApprovals()
        // Same path as HackerAIBoundService.approvePendingAgentTask (confirmed=true).
        if (agentTaskConfirmPolicy.blockReason(llmProvider.hasApiKey(), confirmed = true) == null) {
            agentTaskRunner.runTask(item.inputJson)
        }
        refreshApprovals()
    }

    private fun denyPending(id: String) {
        pendingAgentApprovals.deny(id)
        refreshApprovals()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shizukuManager.init()
        dcaInstalledAtCreate = dependencyGuard.isDcaInstalled()
        refreshApprovals()
        renderContent(dcaInstalledAtCreate)
    }

    override fun onResume() {
        super.onResume()
        // Re-check Shizuku state after the user may have changed it elsewhere.
        shizukuManager.refreshState()
        // Only recreate when DCA install state actually changed to avoid an
        // infinite recreation loop (recreate() → onResume() → recreate() …).
        if (dependencyGuard.isDcaInstalled() != dcaInstalledAtCreate) {
            recreate()
        }
    }

    private fun renderContent(isDcaInstalled: Boolean) {
        setContent {
            HackerAITheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isDcaInstalled) {
                        HackerAINavHost(
                            companionStatus = CompanionStatus(
                                dcaInstalled = isDcaInstalled,
                                llmConfigured = llmProvider.hasApiKey(),
                                shizukuState = shizukuManager.state.value,
                                externalControlEnabled = externalControlPrefs.enabled,
                                pendingApprovals = pendingApprovals.size,
                                health = when {
                                    !isDcaInstalled -> "dca_not_installed"
                                    !llmProvider.hasApiKey() -> "no_llm_provider"
                                    else -> "ok"
                                },
                            ),
                            llmProvider = llmProvider,
                            shizukuManager = shizukuManager,
                            onRequestShizukuPermission = { shizukuManager.requestPermission(this) },
                            requireAgentTaskConfirm = agentTaskConfirmPolicy.requireConfirm,
                            onRequireAgentTaskConfirmChange = { agentTaskConfirmPolicy.requireConfirm = it },
                            externalControlEnabled = externalControlPrefs.enabled,
                            onExternalControlEnabledChange = {
                                externalControlPrefs.enabled = it
                                remoteCommandGate.externalControlEnabled = it
                            },
                            pendingApprovals = pendingApprovals,
                            onRefreshApprovals = { refreshApprovals() },
                            onApprovePending = { approvePending(it) },
                            onDenyPending = { denyPending(it) },
                        )
                    } else {
                        DcaRequiredScreen()
                    }
                }
            }
        }
    }
}
