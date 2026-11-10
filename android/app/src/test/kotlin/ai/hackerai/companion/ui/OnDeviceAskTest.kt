package ai.hackerai.companion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAskTest {
    @Test
    fun `no API key asks the user to open Settings`() {
        val d = OnDeviceAsk.decide("list open ports", hasApiKey = false)
        assertEquals(OnDeviceAsk.Decision.NeedsApiKey, d)
        assertFalse("sk-" in OnDeviceAsk.NEEDS_KEY)
        assertTrue("Settings" in OnDeviceAsk.NEEDS_KEY)
    }

    @Test
    fun `blank prompt is rejected even when a key is saved`() {
        assertEquals(OnDeviceAsk.Decision.EmptyPrompt, OnDeviceAsk.decide("  \n", hasApiKey = true))
    }

    @Test
    fun `Run decision builds CreateAgentInput JSON and is the confirmation path`() {
        val d = OnDeviceAsk.decide("  enumerate subdomains of example.com  ", hasApiKey = true)
        check(d is OnDeviceAsk.Decision.Run)
        assertTrue("on-device-ask" in d.inputJson)
        assertTrue("enumerate subdomains of example.com" in d.inputJson)
        assertTrue("Tapping Run" in OnDeviceAsk.CONFIRM_HINT)
        assertTrue("on-device confirmation" in OnDeviceAsk.CONFIRM_HINT)
    }

    @Test
    fun `parseStart and parsePoll cover queued done and error`() {
        val started = OnDeviceAsk.parseStart("""{"ok":true,"taskId":"abc","status":"queued"}""")
        assertEquals("abc", started.taskId)
        assertTrue(started.inProgress)
        val done = OnDeviceAsk.parsePoll("""{"status":"done","result":"all clear"}""", "abc")
        assertEquals("done", done.status)
        assertEquals("all clear", done.text)
        assertFalse(done.inProgress)
        val err = OnDeviceAsk.parseStart("""{"ok":false,"error":"Invalid CreateAgentInput"}""")
        assertEquals("error", err.status)
        assertFalse(err.inProgress)
    }

    @Test
    fun `views never include an API key field`() {
        val blob = listOf(
            OnDeviceAsk.parseStart("""{"ok":true,"taskId":"t","status":"queued"}"""),
            OnDeviceAsk.parsePoll("""{"status":"error","error":"LLM API error 401"}""", "t"),
        ).joinToString { it.status + it.text }.lowercase()
        assertFalse("sk-" in blob)
        assertFalse("api_key" in blob)
    }
}
