package ai.hackerai.companion

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device confirm before an agent task that would spend the saved API key.
 * Default ON. Power users can turn it off in Settings. Remote `agent.run` is
 * still always `confirmation_required` on [ai.hackerai.companion.remote.RemoteCommandGate]
 * and is never auto-approved by this preference.
 */
@Singleton
class AgentTaskConfirmPolicy @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** When true (default), [HackerAIBoundService.runAgentTask] refuses until an explicit confirm. */
    var requireConfirm: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE, DEFAULT_REQUIRE)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE, value).apply()

    /**
     * @return null if the start is allowed; otherwise a machine code (`confirmation_required`).
     * [confirmed] is true only after an on-device dialog / approve call for this exact start.
     * Remote callers must never pass true.
     */
    fun blockReason(hasApiKey: Boolean, confirmed: Boolean): String? {
        if (!hasApiKey) return null // no key → runner will fail with no_llm_provider / chat error; not a spend path
        if (!requireConfirm) return null
        if (confirmed) return null
        return CODE_CONFIRMATION_REQUIRED
    }

    companion object {
        const val PREFS = "hackerai_agent_confirm"
        const val KEY_REQUIRE = "require_agent_task_confirm"
        const val DEFAULT_REQUIRE = true
        const val CODE_CONFIRMATION_REQUIRED = "confirmation_required"
    }
}
