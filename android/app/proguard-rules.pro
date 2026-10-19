# Keep AIDL-generated service stub and proxy so Binder marshalling survives shrinking.
-keep class ai.droidcommand.companion.IHackerAIService { *; }
-keep class ai.droidcommand.companion.IHackerAIService$Stub { *; }
-keep class ai.droidcommand.companion.IHackerAIService$Stub$Proxy { *; }

# Keep the bound service entry point (exported in the manifest).
-keep class ai.hackerai.companion.service.HackerAIBoundService { *; }

# Keep kotlinx.serialization classes from core-hackerai.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class ai.droidcommand.hackerai.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ai.droidcommand.hackerai.** {
    *** Companion;
    *** serializer(...);
    kotlinx.serialization.KSerializer serializer(kotlin.reflect.KClass);
}

# Hilt — generated components and entry points must survive shrinking.
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Tink / EncryptedSharedPreferences — keep crypto primitives.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Shizuku / Sui — keep the privileged UserService AIDL stub/proxy and its impl,
# plus the Shizuku/Sui runtime classes referenced reflectively across processes.
-keep class ai.hackerai.companion.shizuku.IShizukuUserService { *; }
-keep class ai.hackerai.companion.shizuku.IShizukuUserService$Stub { *; }
-keep class ai.hackerai.companion.shizuku.IShizukuUserService$Stub$Proxy { *; }
-keep class ai.hackerai.companion.shizuku.ShizukuUserService { *; }
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn rikka.sui.**
