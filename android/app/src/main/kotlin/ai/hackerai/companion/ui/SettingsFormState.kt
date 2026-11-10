package ai.hackerai.companion.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Hoisted Settings state so Save Key immediately shows "Key configured"
 * instead of snapping back to the empty field (parent used to pass a stale
 * currentApiKey that was not Compose state).
 */
class SettingsFormState(
    initialApiKey: String?,
    initialModel: String,
    initialEndpoint: String,
    private val persistApiKey: (String) -> Unit,
    private val persistClearApiKey: () -> Unit,
    private val persistModel: (String) -> Unit,
    private val persistEndpoint: (String) -> Unit,
) {
    var apiKey by mutableStateOf(initialApiKey)
        private set
    var model by mutableStateOf(initialModel)
        private set
    var endpoint by mutableStateOf(initialEndpoint)
        private set

    fun saveApiKey(draft: String) {
        val trimmed = draft.trim()
        if (trimmed.isEmpty()) return
        persistApiKey(trimmed)
        apiKey = trimmed
    }

    fun clearApiKey() {
        persistClearApiKey()
        apiKey = null
    }

    fun saveModel(draft: String) {
        val trimmed = draft.trim()
        if (trimmed.isEmpty()) return
        persistModel(trimmed)
        model = trimmed
    }

    fun saveEndpoint(draft: String) {
        val trimmed = draft.trim()
        if (trimmed.isEmpty()) return
        persistEndpoint(trimmed)
        endpoint = trimmed
    }
}
