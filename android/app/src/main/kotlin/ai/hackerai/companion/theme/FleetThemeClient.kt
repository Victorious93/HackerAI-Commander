package ai.hackerai.companion.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Returns the signing-cert SHA-256s of the package that owns [authority], or null if not owned by [expectedPackage]. */
fun interface ProviderVerifier {
    fun verify(context: Context, authority: String, expectedPackage: String): Boolean
}

class PinnedProviderVerifier : ProviderVerifier {
    override fun verify(context: Context, authority: String, expectedPackage: String): Boolean = runCatching {
        val pm = context.packageManager
        val info = pm.resolveContentProvider(authority, 0) ?: return false
        if (info.packageName != expectedPackage) return false
        FleetThemeCodec.isPinned(certSha256s(pm, expectedPackage))
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun certSha256s(pm: PackageManager, pkg: String): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val si = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return emptyList()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory.takeLast(1).toTypedArray()
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        } ?: return emptyList()
        return sigs.map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(":") { "%02X".format(it) }
        }
    }
}

/**
 * Reads the VictorLaunch fleet theme and exposes it live. Never writes the provider.
 * Unpinned provider, SecurityException, or bad schema → [palette] = null (default theme).
 */
@Singleton
class FleetThemeClient internal constructor(
    private val context: Context,
    private val verifier: ProviderVerifier,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(context, PinnedProviderVerifier())

    private val _palette = MutableStateFlow<FleetPalette?>(null)
    val palette: StateFlow<FleetPalette?> = _palette.asStateFlow()
    private var observer: ContentObserver? = null

    fun start() {
        refresh()
        if (observer == null) {
            val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh()
            }
            runCatching {
                context.contentResolver.registerContentObserver(Uri.parse(FleetThemeContract.URI), false, o)
                observer = o
            }
        }
    }

    fun refresh() {
        _palette.value = query()
    }

    internal fun query(): FleetPalette? = try {
        if (!verifier.verify(context, FleetThemeContract.AUTHORITY, FleetThemeContract.PROVIDER_PACKAGE)) {
            null
        } else {
            context.contentResolver.query(Uri.parse(FleetThemeContract.URI), null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val idx = c.getColumnIndex(FleetThemeContract.COLUMN_JSON)
                if (idx < 0) null else FleetThemeCodec.decode(c.getString(idx))
            }
        }
    } catch (_: SecurityException) {
        null // not pinned on VictorLaunch's side
    } catch (_: Exception) {
        null
    }
}

/** Wake-up only; extras are never trusted. Re-queries the pinned provider. */
@AndroidEntryPoint
class FleetThemeChangedReceiver : BroadcastReceiver() {
    @Inject lateinit var client: FleetThemeClient

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FleetThemeContract.ACTION_CHANGED) client.refresh()
    }
}
