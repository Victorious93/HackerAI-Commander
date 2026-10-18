import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    kotlin("plugin.serialization") version "2.1.0"
}

// --- Version derivation ------------------------------------------------------
// On a tag build, CI exposes the tag via GITHUB_REF_NAME (e.g. "v1.2.3").
// Derive versionName/versionCode from a vMAJOR.MINOR.PATCH tag; local and
// non-tag builds fall back to stable defaults so nothing breaks.
val fallbackVersionName = "0.1.0"
val fallbackVersionCode = 1

val releaseSemver: Triple<Int, Int, Int>? = run {
    val ref = System.getenv("GITHUB_REF_NAME")?.trim().orEmpty()
    Regex("""^v(\d+)\.(\d+)\.(\d+)$""").find(ref)?.let {
        val (maj, min, pat) = it.destructured
        Triple(maj.toInt(), min.toInt(), pat.toInt())
    }
}

val appVersionName: String =
    releaseSemver?.let { (a, b, c) -> "$a.$b.$c" } ?: fallbackVersionName
// Semver-encoded, strictly increasing: MAJOR*10000 + MINOR*100 + PATCH
// (minor/patch < 100). Falls back to 1 for untagged/local builds.
val appVersionCode: Int =
    releaseSemver?.let { (a, b, c) -> a * 10000 + b * 100 + c } ?: fallbackVersionCode

android {
    namespace = "ai.hackerai.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.hackerai.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // Dedicated, stable DEBUG keystore for this app (not a secret). Release signing
        // is intentionally not configured here; it will come from CI secrets.
        getByName("debug") {
            storeFile = rootProject.file("keystore/hackeraietc-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Release signing is driven entirely by environment variables, supplied in
        // CI from repository secrets (the shared fleet RELEASE key). When any of them
        // is absent — local builds, PRs, forks — the keystore is left unset and the
        // release build is produced UNSIGNED instead of failing. No secret is ever
        // written into the repo; the base64 keystore is decoded only into build/.
        create("release") {
            val storeB64 = System.getenv("ANDROID_RELEASE_KEYSTORE_B64")
            val storePass = System.getenv("ANDROID_RELEASE_STORE_PASSWORD")
            val alias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
            val keyPass = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
            if (!storeB64.isNullOrBlank() && !storePass.isNullOrBlank() &&
                !alias.isNullOrBlank() && !keyPass.isNullOrBlank()
            ) {
                // The shared fleet release key is a PKCS12 keystore, so default
                // storeType to "pkcs12"; ANDROID_RELEASE_STORE_TYPE can override
                // (e.g. "jks") if a different keystore is ever used.
                val type = System.getenv("ANDROID_RELEASE_STORE_TYPE")?.takeIf { it.isNotBlank() }
                    ?: "pkcs12"
                val ext = if (type.equals("pkcs12", ignoreCase = true)) "p12" else "jks"
                val ksFile = layout.buildDirectory.file("release-keystore.$ext").get().asFile
                ksFile.parentFile.mkdirs()
                ksFile.writeBytes(Base64.getDecoder().decode(storeB64))
                storeFile = ksFile
                storeType = type
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign only when the env-driven config actually resolved a keystore;
            // otherwise leave the release APK unsigned so keyless builds still succeed.
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) releaseSigning else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // API key encrypted at rest via Android Keystore + AES-256-GCM.
    implementation(libs.androidx.security.crypto)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
