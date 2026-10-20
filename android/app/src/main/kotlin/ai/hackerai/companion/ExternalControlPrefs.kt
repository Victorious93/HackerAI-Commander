package ai.hackerai.companion

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fleet external-control switch. **OFF by default.** Turning it on lets a BIND_HACKERAI
 * caller use `executeEnvelope` ops after the rest of the trust pipeline. Config import
 * must never leave this ON (see UNIFIED_BACKUP.md §4 step 7).
 */
@Singleton
class ExternalControlPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY, DEFAULT)
        set(value) = prefs.edit().putBoolean(KEY, value).apply()

    companion object {
        const val PREFS = "hackerai_external_control"
        const val KEY = "external_control_enabled"
        const val DEFAULT = false
    }
}
