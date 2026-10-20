package ai.hackerai.companion.service

import ai.droidcommand.companion.IHackerAIService
import ai.hackerai.companion.DependencyGuard
import ai.hackerai.companion.remote.RemoteDispatcher
import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HackerAIBoundService : Service() {
    @Inject lateinit var agentTaskRunner: AgentTaskRunner
    @Inject lateinit var dependencyGuard: DependencyGuard
    @Inject lateinit var remoteDispatcher: RemoteDispatcher

    private val binder =
        object : IHackerAIService.Stub() {
            override fun runAgentTask(inputJson: String): String = this@HackerAIBoundService.runAgentTask(inputJson)
            override fun getSkillCatalog(): String = this@HackerAIBoundService.getSkillCatalog()
            override fun validateFinding(candidateJson: String): String = this@HackerAIBoundService.validateFinding(candidateJson)
            override fun cancelTask(taskId: String) = agentTaskRunner.cancelTask(taskId)
            override fun getTaskResult(taskId: String): String = this@HackerAIBoundService.getTaskResult(taskId)
            override fun healthCheck(): String = this@HackerAIBoundService.healthCheck()
            override fun executeEnvelope(envelopeJson: String): String = this@HackerAIBoundService.executeEnvelope(envelopeJson)
        }

    override fun onBind(intent: Intent): IBinder = binder

    fun runAgentTask(inputJson: String): String {
        if (!dependencyGuard.isDcaInstalled()) return errorJson("DroidCommand AI is not installed")
        return agentTaskRunner.runTask(inputJson)
    }

    fun getSkillCatalog(): String {
        if (!dependencyGuard.isDcaInstalled()) return errorJson("DroidCommand AI is not installed")
        return agentTaskRunner.getSkillCatalog()
    }

    fun validateFinding(candidateJson: String): String {
        if (!dependencyGuard.isDcaInstalled()) return errorJson("DroidCommand AI is not installed")
        return agentTaskRunner.validateFinding(candidateJson)
    }

    fun getTaskResult(taskId: String): String {
        if (!dependencyGuard.isDcaInstalled()) return errorJson("DroidCommand AI is not installed")
        return agentTaskRunner.getTaskResult(taskId)
    }

    fun healthCheck(): String {
        if (!dependencyGuard.isDcaInstalled()) return "dca_not_installed"
        return agentTaskRunner.healthCheck()
    }

    /**
     * Fleet remote-control envelope entry point. DependencyGuard still applies; the gate then
     * enforces identity / external-control / replay / confirm. Destructive actionRefs never run.
     */
    fun executeEnvelope(envelopeJson: String): String {
        if (!dependencyGuard.isDcaInstalled()) return errorJson("DroidCommand AI is not installed")
        return remoteDispatcher.dispatch(envelopeJson)
    }

    private fun errorJson(message: String): String {
        val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"ok":false,"error":"$escaped"}"""
    }
}
