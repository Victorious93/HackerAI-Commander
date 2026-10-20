package ai.hackerai.companion.remote

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class PendingRemoteOp(
    val id: String,
    val command: String,
    val envelopeJson: String,
    val createdAtMs: Long,
    val preview: String,
)

/**
 * On-device queue for remote destructive envelope ops (`agent.run`, `finding.validate`,
 * `task.cancel`). `shizuku.shell` is never queued (local-only). Approve re-dispatches with
 * confirmed=true; never auto-approves.
 */
@Singleton
class PendingRemoteApprovals @Inject constructor() {
    private val pending = ConcurrentHashMap<String, PendingRemoteOp>()

    fun enqueue(command: String, envelopeJson: String, preview: String, nowMs: Long = System.currentTimeMillis()): PendingRemoteOp {
        val item = PendingRemoteOp(UUID.randomUUID().toString(), command, envelopeJson, nowMs, preview)
        pending[item.id] = item
        return item
    }

    fun list(): List<PendingRemoteOp> = pending.values.sortedBy { it.createdAtMs }
    fun take(id: String): PendingRemoteOp? = pending.remove(id)
    fun deny(id: String): Boolean = pending.remove(id) != null
    fun size(): Int = pending.size
}
