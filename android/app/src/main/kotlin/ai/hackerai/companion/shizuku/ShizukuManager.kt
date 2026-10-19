package ai.hackerai.companion.shizuku

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.ActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/** The four distinct states the UI renders, per the fleet Shizuku standard. */
enum class ShizukuState {
    /** Neither the Shizuku app nor Sui is present. */
    NOT_INSTALLED,

    /** Installed, but the Shizuku/Sui service is not currently running. */
    NOT_RUNNING,

    /** Service is running but this app has not been granted permission. */
    DENIED,

    /** Service is running and permission is granted — privileged ops are available. */
    GRANTED,
}

/**
 * Central Shizuku/Sui integration point.
 *
 * - Tracks a four-state [StateFlow] (not-installed / not-running / denied / granted).
 * - Requests the `moe.shizuku.manager.permission.API_V23` permission through the
 *   Shizuku permission API (with a result listener), falling back to the runtime
 *   permission flow on pre-v11 Shizuku.
 * - Binds a privileged [ShizukuUserService] and runs commands off the main thread.
 *
 * Graceful fallback is the whole point: if Shizuku is missing/stopped/denied, the
 * app keeps working unprivileged — callers just get a thrown [ShizukuUnavailable]
 * when they try to run a privileged command, and the UI shows how to enable it.
 */
@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val bindMutex = Mutex()

    @Volatile
    private var userService: IShizukuUserService? = null
    private var connection: ServiceConnection? = null
    private var listenersAdded = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("shizuku_svc")
            .debuggable(isDebuggable())
            .version(SERVICE_VERSION)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener { refreshState() }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
        refreshState()
    }
    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refreshState() }

    /** Register Shizuku listeners. Safe to call more than once. */
    fun init() {
        if (!listenersAdded) {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            listenersAdded = true
        }
        refreshState()
    }

    /** Recompute the current state (e.g. after returning from the Shizuku app). */
    fun refreshState() {
        _state.value = computeState()
    }

    private fun computeState(): ShizukuState {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (alive) {
            return if (hasPermission()) ShizukuState.GRANTED else ShizukuState.DENIED
        }
        return if (isShizukuInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
    }

    private fun hasPermission(): Boolean =
        runCatching {
            if (Shizuku.isPreV11()) {
                context.checkSelfPermission(SHIZUKU_PERMISSION) == PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        }.getOrDefault(false)

    private fun isShizukuInstalled(): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_MANAGER_PACKAGE, 0)
            true
        }.getOrDefault(false)

    /**
     * Request Shizuku permission. Uses the Shizuku permission API (result arrives
     * on [permissionResultListener]); on pre-v11 Shizuku it falls back to the
     * standard runtime permission dialog, which needs an [Activity].
     */
    fun requestPermission(activity: Activity?) {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            refreshState()
            return
        }
        if (hasPermission()) {
            refreshState()
            return
        }
        runCatching {
            if (Shizuku.isPreV11()) {
                if (activity != null) {
                    ActivityCompat.requestPermissions(
                        activity, arrayOf(SHIZUKU_PERMISSION), PERMISSION_REQUEST_CODE,
                    )
                }
            } else {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        }
    }

    /**
     * Run a privileged shell [command] via the bound user service, off the main
     * thread. Throws [ShizukuUnavailable] if Shizuku is not granted/running.
     *
     * Callers MUST confirm explicit user intent before invoking this.
     */
    suspend fun exec(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): ShellResult =
        withContext(Dispatchers.IO) {
            if (computeState() != ShizukuState.GRANTED) {
                throw ShizukuUnavailable("Shizuku privileged access is not available")
            }
            val service = ensureService()
            val json = service.execCommand(command, timeoutMs)
            ShellResult.fromJson(json)
        }

    private suspend fun ensureService(): IShizukuUserService =
        bindMutex.withLock {
            userService?.let { existing ->
                if (existing.asBinder()?.isBinderAlive == true) return existing
            }
            val deferred = CompletableDeferred<IShizukuUserService>()
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null || !binder.pingBinder()) {
                        if (!deferred.isCompleted) {
                            deferred.completeExceptionally(
                                ShizukuUnavailable("User service binder was not alive"),
                            )
                        }
                        return
                    }
                    val svc = IShizukuUserService.Stub.asInterface(binder)
                    userService = svc
                    if (!deferred.isCompleted) deferred.complete(svc)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    userService = null
                }
            }
            connection = conn
            runCatching { Shizuku.bindUserService(userServiceArgs, conn) }
                .onFailure { throw ShizukuUnavailable("Failed to bind user service: ${it.message}") }
            withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        }

    /** Unbind the privileged service (best-effort). */
    fun unbind() {
        connection?.let { runCatching { Shizuku.unbindUserService(userServiceArgs, it, true) } }
        connection = null
        userService = null
    }

    private fun isDebuggable(): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    companion object {
        const val SHIZUKU_PERMISSION = "moe.shizuku.manager.permission.API_V23"
        const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST_CODE = 4211
        const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val BIND_TIMEOUT_MS = 15_000L
        private const val SERVICE_VERSION = 1
    }
}

/** Thrown when a privileged command is attempted without Shizuku being available. */
class ShizukuUnavailable(message: String) : Exception(message)
