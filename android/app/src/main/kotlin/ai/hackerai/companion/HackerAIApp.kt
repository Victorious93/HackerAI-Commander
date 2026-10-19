package ai.hackerai.companion

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import rikka.sui.Sui

@HiltAndroidApp
class HackerAIApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable Sui support when present (Sui is a Magisk/Zygisk-based Shizuku
        // backend). Harmless/no-op on devices without Sui; Shizuku still works.
        runCatching { Sui.init(packageName) }
    }
}
