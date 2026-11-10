package ai.hackerai.companion.ui

import ai.hackerai.companion.llm.LlmConnectionProbe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormStateTest {
    private fun form(
        key: String? = null,
        model: String = "claude-haiku-4-5-20251001",
        endpoint: String = "https://api.anthropic.com/v1/messages",
    ): Pair<SettingsFormState, MutableMap<String, String?>> {
        val store = mutableMapOf<String, String?>(
            "key" to key,
            "model" to model,
            "endpoint" to endpoint,
        )
        val form = SettingsFormState(
            initialApiKey = key,
            initialModel = model,
            initialEndpoint = endpoint,
            persistApiKey = { store["key"] = it },
            persistClearApiKey = { store["key"] = null },
            persistModel = { store["model"] = it },
            persistEndpoint = { store["endpoint"] = it },
        )
        return form to store
    }

    @Test
    fun `saveApiKey updates visible state immediately`() {
        val (form, store) = form()
        assertNull(form.apiKey)
        form.saveApiKey("  sk-test-1234  ")
        assertEquals("sk-test-1234", form.apiKey)
        assertEquals("sk-test-1234", store["key"])
    }

    @Test
    fun `blank save leaves the form unchanged`() {
        val (form, store) = form()
        form.saveApiKey("   ")
        assertNull(form.apiKey)
        assertNull(store["key"])
    }

    @Test
    fun `clearApiKey removes the configured key from state`() {
        val (form, _) = form(key = "sk-test-9999")
        form.clearApiKey()
        assertNull(form.apiKey)
    }

    @Test
    fun `save model and endpoint update state`() {
        val (form, store) = form()
        form.saveModel("claude-sonnet")
        form.saveEndpoint("https://example.invalid/v1/messages")
        assertEquals("claude-sonnet", form.model)
        assertEquals("https://example.invalid/v1/messages", form.endpoint)
        assertEquals("claude-sonnet", store["model"])
        assertEquals("https://example.invalid/v1/messages", store["endpoint"])
    }
}

class LlmConnectionProbeTest {
    @Test
    fun `classifies success 401 and 404 without exposing a key`() {
        assertTrue(LlmConnectionProbe.classify(200).ok)
        assertEquals("Connection succeeded (HTTP 200).", LlmConnectionProbe.classify(200).summary)
        val u = LlmConnectionProbe.classify(401)
        assertFalse(u.ok)
        assertEquals(401, u.status)
        assertTrue("401" in u.summary)
        val n = LlmConnectionProbe.classify(404)
        assertFalse(n.ok)
        assertEquals(404, n.status)
        assertTrue("404" in n.summary)
        listOf(200, 401, 403, 404, 500).forEach { code ->
            val text = LlmConnectionProbe.classify(code).summary.lowercase()
            assertFalse("sk-" in text)
            assertFalse("api_key" in text)
            assertFalse("api-key" in text)
        }
    }

    @Test
    fun `sanitize and fromThrowable never echo the API key`() {
        val key = "sk-secret-ABCDEF"
        assertEquals("status … denied", LlmConnectionProbe.sanitize("status $key denied", key))
        val r = LlmConnectionProbe.fromThrowable(RuntimeException("LLM API error 401: $key"), key)
        assertEquals(401, r.status)
        assertFalse(key in r.summary)
        val net = LlmConnectionProbe.fromThrowable(java.net.UnknownHostException("api.$key.example"), key)
        assertFalse(key in net.summary)
        assertFalse(net.ok)
    }
}
