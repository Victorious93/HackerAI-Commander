package ai.hackerai.companion.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * On-device Ask / Run task: the user types a prompt and taps Run.
 * Tapping Run is the on-device confirmation (call [ai.hackerai.companion.service.AgentTaskRunner.runTask]
 * directly). This path never queues a remote envelope and never calls shizuku.shell.
 */
object OnDeviceAsk {
    const val TITLE = "Ask"
    const val RUN_BUTTON = "Run"
    const val AGENT_NAME = "on-device-ask"
    const val MAX_TASK_CHARS = 4_000
    const val NEEDS_KEY =
        "Add an API key in Settings before running a task. The key is never shown here."
    const val CONFIRM_HINT =
        "Tapping Run uses your saved API key and is the on-device confirmation for this task."

    sealed class Decision {
        data object NeedsApiKey : Decision()
        data object EmptyPrompt : Decision()
        data class Run(val inputJson: String) : Decision()
    }

    data class TaskView(
        val status: String,
        val text: String,
        val inProgress: Boolean,
        val taskId: String? = null,
    )

    fun decide(prompt: String, hasApiKey: Boolean): Decision {
        if (!hasApiKey) return Decision.NeedsApiKey
        val task = prompt.trim()
        if (task.isEmpty()) return Decision.EmptyPrompt
        return Decision.Run(toInputJson(task.take(MAX_TASK_CHARS)))
    }

    fun toInputJson(task: String): String {
        val esc = task.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """{"name":"$AGENT_NAME","task":"$esc"}"""
    }

    fun parseStart(runJson: String): TaskView {
        val o = runCatching { Json.parseToJsonElement(runJson).jsonObject }.getOrNull()
            ?: return TaskView("error", runJson, inProgress = false)
        val taskId = o["taskId"]?.jsonPrimitive?.contentOrNull
        val error = o["error"]?.jsonPrimitive?.contentOrNull
        return if (taskId != null) {
            TaskView("queued", "Running…", inProgress = true, taskId = taskId)
        } else {
            TaskView("error", error ?: runJson, inProgress = false)
        }
    }

    fun parsePoll(resultJson: String, taskId: String?): TaskView {
        val o = runCatching { Json.parseToJsonElement(resultJson).jsonObject }.getOrNull()
            ?: return TaskView("error", resultJson, inProgress = false, taskId = taskId)
        val status = o["status"]?.jsonPrimitive?.contentOrNull ?: "error"
        return when (status) {
            "queued", "running" -> TaskView(status, "Running…", inProgress = true, taskId = taskId)
            "done" -> TaskView("done", o["result"]?.jsonPrimitive?.contentOrNull ?: "", inProgress = false, taskId = taskId)
            "cancelled" -> TaskView("cancelled", "Cancelled.", inProgress = false, taskId = taskId)
            else -> TaskView(status, o["error"]?.jsonPrimitive?.contentOrNull ?: resultJson, inProgress = false, taskId = taskId)
        }
    }
}
