package ai.hackerai.companion.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingRemoteApprovalsTest {
    @Test fun `enqueue take deny`() {
        val q = PendingRemoteApprovals()
        val a = q.enqueue("agent.run", "{}", "preview", 1)
        assertEquals(1, q.size())
        assertEquals(a.id, q.list().single().id)
        assertEquals(a, q.take(a.id))
        assertNull(q.take(a.id))
        val b = q.enqueue("task.cancel", "{}", "c", 2)
        assertEquals(true, q.deny(b.id))
        assertEquals(0, q.size())
    }
}
