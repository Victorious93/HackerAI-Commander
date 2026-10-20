package ai.hackerai.companion

import ai.hackerai.companion.remote.BRIDGE_PROTOCOL_MAJOR
import ai.hackerai.companion.remote.BRIDGE_PROTOCOL_MINOR
import ai.hackerai.companion.shizuku.ShizukuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionStatusTest {
    @Test fun `rows expose health fields without secrets`() {
        val s = CompanionStatus(
            dcaInstalled = true,
            llmConfigured = true,
            shizukuState = ShizukuState.GRANTED,
            externalControlEnabled = false,
            pendingApprovals = 2,
            health = "ok",
        )
        val text = s.rows().joinToString { "${it.first}=${it.second}" }.lowercase()
        assertTrue("key configured" in text)
        assertTrue("off (default)" in text)
        assertEquals("2", s.rows().first { it.first == "Pending approvals" }.second)
        assertEquals("$BRIDGE_PROTOCOL_MAJOR.$BRIDGE_PROTOCOL_MINOR", s.rows().first { it.first == "Bridge protocol" }.second)
        listOf("api_key", "sk-", "password", "prompt", "chat").forEach { assertFalse(it in text) }
    }
}
