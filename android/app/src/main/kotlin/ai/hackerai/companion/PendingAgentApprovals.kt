package ai.hackerai.companion

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class PendingAgentTask(
    val id: String,
    val inputJson: String,
    val createdAtMs: Long,
    /** Short, safe preview (task field truncated). Never includes API keys. */
    val taskPreview: String,
)

/**
 * On-device queue for AIDL [ai.hackerai.companion.service.HackerAIBoundService.runAgentTask]
 * starts blocked by [AgentTaskConfirmPolicy]. Approve calls run with confirmed=true.
 * Remote `agent.run` does **not** use this queue.
 */
@Singleton
class PendingAgentApprovals @Inject constructor() {
    private val pending = ConcurrentHashMap<String, PendingAgentTask>()

    fun enqueue(inputJson: String, nowMs: Long = System.currentTimeMillis()): PendingAgentTask {
        val preview = extractTaskPreview(inputJson)
        val item = PendingAgentTask(UUID.randomUUID().toString(), inputJson, nowMs, preview)
        pending[item.id] = item
        return item
    }

    fun list(): List<PendingAgentTask> = pending.values.sortedBy { it.createdAtMs }

    fun peek(id: String): PendingAgentTask? = pending[id]

    /** Removes and returns the item, or null if unknown/already handled. */
    fun take(id: String): PendingAgentTask? = pending.remove(id)

    fun deny(id: String): Boolean = pending.remove(id) != null

    fun size(): Int = pending.size

    companion object {
        private val TASK_REGEX = Regex(""""task"\s*:\s*"((?:\\.|[^"\\])*)"""")

        fun extractTaskPreview(inputJson: String, maxLen: Int = 120): String {
            val raw = TASK_REGEX.find(inputJson)?.groupValues?.getOrNull(1)
                ?.replace("\\\"", "\"")
                ?.replace("\\n", " ")
                ?: "(no task field)"
            return if (raw.length <= maxLen) raw else raw.take(maxLen - 1) + "…"
        }
    }
}
