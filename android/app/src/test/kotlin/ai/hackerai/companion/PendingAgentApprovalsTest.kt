package ai.hackerai.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingAgentApprovalsTest {
    private val q = PendingAgentApprovals()

    @Test fun `enqueue lists and take removes`() {
        val a = q.enqueue("""{"name":"t","task":"scan example.com"}""", nowMs = 1)
        assertEquals(1, q.size())
        assertEquals("scan example.com", a.taskPreview)
        assertEquals(a.id, q.list().single().id)
        assertEquals(a.inputJson, q.take(a.id)?.inputJson)
        assertNull(q.take(a.id))
        assertEquals(0, q.size())
    }

    @Test fun `deny removes without returning payload`() {
        val a = q.enqueue("""{"name":"t","task":"x"}""", 2)
        assertTrue(q.deny(a.id))
        assertNull(q.peek(a.id))
    }

    @Test fun `preview truncates and never invents secrets`() {
        val long = "a".repeat(200)
        val p = PendingAgentApprovals.extractTaskPreview("""{"task":"$long"}""")
        assertTrue(p.endsWith("…"))
        assertTrue(p.length <= 120)
        assertEquals("(no task field)", PendingAgentApprovals.extractTaskPreview("{}"))
    }
}
