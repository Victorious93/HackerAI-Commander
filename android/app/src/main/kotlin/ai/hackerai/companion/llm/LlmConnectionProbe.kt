package ai.hackerai.companion.llm

/** Result of a Settings "Test connection" probe. Never includes the API key. */
data class LlmConnectionResult(
    val ok: Boolean,
    val status: Int?,
    val summary: String,
)

object LlmConnectionProbe {
    fun classify(status: Int): LlmConnectionResult = when (status) {
        in 200..299 -> LlmConnectionResult(true, status, "Connection succeeded (HTTP $status).")
        401 -> LlmConnectionResult(false, 401, "Unauthorized (HTTP 401). Check the API key.")
        403 -> LlmConnectionResult(false, 403, "Forbidden (HTTP 403). Check the API key.")
        404 -> LlmConnectionResult(false, 404, "Not found (HTTP 404). Check the endpoint URL.")
        else -> LlmConnectionResult(false, status, "Request failed (HTTP $status).")
    }

    fun fromThrowable(error: Throwable, apiKey: String?): LlmConnectionResult {
        val status = Regex("""\b([1-5][0-9]{2})\b""").find(error.message ?: "")?.groupValues?.get(1)?.toInt()
        if (status != null) {
            return classify(status)
        }
        val message = sanitize(error.message ?: error.javaClass.simpleName, apiKey)
        return LlmConnectionResult(false, null, "Could not reach the endpoint. $message")
    }

    fun sanitize(text: String, apiKey: String?): String {
        if (apiKey.isNullOrEmpty()) return text
        return text.replace(apiKey, "…")
    }
}
