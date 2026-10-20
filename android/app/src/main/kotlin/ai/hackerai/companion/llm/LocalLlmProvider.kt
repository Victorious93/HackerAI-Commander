package ai.hackerai.companion.llm

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable LLM interface for [ai.hackerai.companion.service.AgentTaskRunner].
 *
 * Keeping this as an interface makes [AgentTaskRunner] testable without
 * Android emulator — test code injects a mock.
 */
interface LocalLlmProvider {
    /** Returns true if an API key has been configured. */
    fun hasApiKey(): Boolean

    /**
     * Performs a single-turn chat completion.
     *
     * @param systemPrompt The agent's system context.
     * @param userMessage  The user/task content for this step.
     * @param step         Current step index (0-based); provider may use it for temperature scaling.
     * @return             The model's response text.
     * @throws Exception   On network failure, auth error, or content block (caller classifies via [ai.droidcommand.hackerai.classifyProviderError]).
     */
    fun chat(systemPrompt: String, userMessage: String, step: Int): String
}

/**
 * HTTP-backed LLM provider. Stores the API key in [EncryptedSharedPreferences]
 * (Android Keystore + AES-256-GCM). Never writes the key to disk in plaintext.
 *
 * The endpoint defaults to the Anthropic Messages API. The wire format is always
 * Anthropic Messages (`x-api-key`, `anthropic-version`, messages body) — not OpenAI
 * chat/completions. Pointing Settings at stock Ollama/LM Studio OpenAI URLs will fail
 * unless a proxy translates Anthropic → the local server.
 *
 * NOT RUNTIME VERIFIED — no Android SDK in this build environment.
 */
@Singleton
class HttpLocalLlmProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocalLlmProvider {

    companion object {
        private const val PREFS_FILE = "hackerai_secure_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_ENDPOINT = "endpoint"
        const val DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val DEFAULT_MODEL = "claude-haiku-4-5-20251001"
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun setApiKey(key: String) = prefs.edit().putString(KEY_API_KEY, key.trim()).apply()
    fun clearApiKey() = prefs.edit().remove(KEY_API_KEY).apply()
    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotEmpty() }

    fun setModel(model: String) = prefs.edit().putString(KEY_MODEL, model).apply()
    fun getModel(): String = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

    fun setEndpoint(url: String) = prefs.edit().putString(KEY_ENDPOINT, url).apply()
    fun getEndpoint(): String = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT

    override fun hasApiKey(): Boolean = getApiKey() != null

    /**
     * Single-turn completion via the Anthropic Messages API (or an OpenAI-compatible
     * endpoint if the user has overridden the endpoint setting).
     *
     * Uses [java.net.HttpURLConnection] — no third-party HTTP client dependency.
     * Blocks the calling thread; [AgentTaskRunner] runs this on a cached thread pool.
     */
    override fun chat(systemPrompt: String, userMessage: String, step: Int): String {
        val apiKey = getApiKey()
            ?: throw IllegalStateException("No API key configured. Go to Settings to add one.")
        val endpoint = getEndpoint()
        val model = getModel()

        val body = buildRequestBody(systemPrompt, userMessage, model)
        val url = java.net.URL(endpoint)
        val conn = url.openConnection() as java.net.HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-api-key", apiKey)
            conn.setRequestProperty("anthropic-version", "2023-06-01")
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.doOutput = true

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = conn.responseCode
            if (status !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $status"
                throw java.io.IOException("LLM API error $status: $err")
            }
            val responseJson = conn.inputStream.bufferedReader().readText()
            extractContent(responseJson)
        } finally {
            conn.disconnect()
        }
    }

    private fun buildRequestBody(systemPrompt: String, userMessage: String, model: String): String {
        val systemEsc = systemPrompt.replace("\\", "\\\\").replace("\"", "\\\"")
        val userEsc = userMessage.replace("\\", "\\\\").replace("\"", "\\\"")
        return """{"model":"$model","max_tokens":4096,"system":"$systemEsc","messages":[{"role":"user","content":"$userEsc"}]}"""
    }

    private fun extractContent(responseJson: String): String {
        // Minimal parse: extract first text content block from Anthropic response.
        // Pattern: "content":[{"type":"text","text":"..."}]
        val marker = "\"text\":\""
        val start = responseJson.indexOf(marker)
        if (start < 0) return responseJson
        val contentStart = start + marker.length
        var i = contentStart
        val sb = StringBuilder()
        while (i < responseJson.length) {
            val c = responseJson[i]
            if (c == '\\' && i + 1 < responseJson.length) {
                when (responseJson[i + 1]) {
                    '"' -> { sb.append('"'); i += 2; continue }
                    'n' -> { sb.append('\n'); i += 2; continue }
                    '\\' -> { sb.append('\\'); i += 2; continue }
                    else -> { sb.append('\\') }
                }
            } else if (c == '"') {
                break
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
