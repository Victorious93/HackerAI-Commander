package ai.hackerai.companion.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fleet theme schemaVersion 1 (victorlaunch docs/specs/FLEET_THEME.md). Read-only client
 * side: VictorLaunch is the sole writer.
 */
object FleetThemeContract {
    const val PROVIDER_PACKAGE = "com.victorious.launch"
    const val AUTHORITY = "com.victorious.launch.theme"
    const val URI = "content://$AUTHORITY/current"
    const val ACTION_CHANGED = "com.victorious.launch.action.FLEET_THEME_CHANGED"
    const val SCHEMA_VERSION = 1
    const val COLUMN_JSON = "json"

    /**
     * VictorLaunch debug signing cert, published in victorlaunch keystore/README.md.
     * Old `1B:06:…:BA:CA` is revoked and intentionally absent. Release cert is not
     * published yet, so a release-signed VictorLaunch falls back to the default theme.
     */
    val PINNED_PROVIDER_CERTS: Set<String> = setOf(
        "B9:9A:AF:E2:C1:41:8B:5D:64:AA:0D:4B:AC:61:FC:F7:A7:BA:81:44:35:E0:EF:89:E8:AA:55:BE:56:4E:4A:69",
    )
}

/** Colors as ARGB ints. */
data class FleetPalette(
    val mode: String,
    val background: Int,
    val surface: Int,
    val primary: Int,
    val secondary: Int,
    val onBackground: Int,
    val muted: Int,
    val danger: Int,
)

object FleetThemeCodec {
    private val json = Json { ignoreUnknownKeys = true }

    /** `#RRGGBB` or `#AARRGGBB` → ARGB int, else null. */
    fun parseHex(s: String?): Int? {
        if (s == null || !s.startsWith("#")) return null
        val h = s.substring(1)
        if (h.length != 6 && h.length != 8) return null
        val v = h.toLongOrNull(16) ?: return null
        return (if (h.length == 6) v or 0xFF000000L else v).toInt()
    }

    /** Returns null for malformed JSON, schemaVersion != 1, or any missing/bad palette color. */
    fun decode(raw: String?): FleetPalette? = runCatching {
        val o = json.parseToJsonElement(raw ?: return null).jsonObject
        if (o["schemaVersion"]?.jsonPrimitive?.intOrNull != FleetThemeContract.SCHEMA_VERSION) return null
        val p = o["palette"]?.jsonObject ?: return null
        fun c(k: String) = parseHex(p[k]?.jsonPrimitive?.content)
        FleetPalette(
            mode = o["mode"]?.jsonPrimitive?.content ?: "",
            background = c("background") ?: return null,
            surface = c("surface") ?: return null,
            primary = c("primary") ?: return null,
            secondary = c("secondary") ?: return null,
            onBackground = c("onBackground") ?: return null,
            muted = c("muted") ?: return null,
            danger = c("danger") ?: return null,
        )
    }.getOrNull()

    /** Single-signer, colon-hex, case-insensitive pin match. */
    fun isPinned(certSha256s: List<String>, pins: Set<String> = FleetThemeContract.PINNED_PROVIDER_CERTS): Boolean {
        if (certSha256s.size != 1) return false
        val norm = { s: String -> s.uppercase().replace(":", "") }
        val pinNorm = pins.map(norm).toSet()
        return norm(certSha256s.single()) in pinNorm
    }
}
