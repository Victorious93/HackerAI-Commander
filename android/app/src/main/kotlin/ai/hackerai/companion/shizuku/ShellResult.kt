package ai.hackerai.companion.shizuku

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Result of a privileged shell command. Serialized to JSON so it can cross the
 * Binder boundary between the Shizuku user-service process and the app process
 * as a single String (keeps the AIDL interface stable).
 */
@Serializable
data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun fromJson(json: String): ShellResult = JSON.decodeFromString(serializer(), json)
    }
}
