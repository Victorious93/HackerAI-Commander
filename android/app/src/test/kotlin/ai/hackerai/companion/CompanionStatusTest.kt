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
        assertTrue("installed (not a bind check)" in text)
    }

    @Test fun `setup steps are honest and never include secrets`() {
        val s = CompanionStatus(
            dcaInstalled = true,
            llmConfigured = false,
            shizukuState = ShizukuState.NOT_INSTALLED,
            externalControlEnabled = false,
            pendingApprovals = 0,
            health = "no_llm_provider",
        )
        val steps = s.setupSteps()
        assertEquals(listOf("dca", "key", "shizuku", "external"), steps.map { it.id })
        val dca = steps.first { it.id == "dca" }
        assertTrue(dca.done)
        assertTrue("not the same as bound" in dca.detail)
        val key = steps.first { it.id == "key" }
        assertFalse(key.done)
        assertEquals("Add API key", key.actionLabel)
        val ext = steps.first { it.id == "external" }
        assertTrue(ext.optional)
        assertFalse(ext.done)
        assertTrue("OFF by default" in ext.detail)
        assertTrue("on-device confirm" in ext.detail)
        val blob = (steps.joinToString { it.title + it.detail + (it.actionLabel ?: "") } + s.rows().toString()).lowercase()
        listOf("api_key", "sk-", "password", "prompt", "chat content").forEach { assertFalse(it in blob) }
    }
}

