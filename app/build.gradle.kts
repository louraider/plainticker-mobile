plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// ---- Version -------------------------------------------------------------------------------
// Driven by -PversionCode=<int> -PversionName=<string>; release.yml derives both from the git
// tag (v1.2.3 -> versionName 1.2.3, versionCode 10203). Local builds fall back to 1 / 0.1.0.
val appVersionCode: Int = providers.gradleProperty("versionCode").orNull?.let { raw ->
    raw.toIntOrNull()?.takeIf { it > 0 }
        ?: throw GradleException("-PversionCode must be a positive integer, got '$raw'")
} ?: 1
val appVersionName: String =
    providers.gradleProperty("versionName").orNull?.takeIf { it.isNotBlank() } ?: "0.1.0"

// ---- Release signing -----------------------------------------------------------------------
// Environment only: never a checked-in file, never gradle.properties or local.properties.
// All four variables must be present, otherwise the release build type stays UNSIGNED
// (output is app-release-unsigned.apk) and one warning line is printed when a release-ish
// task is requested (only while the configuration phase runs: a configuration-cache hit skips
// it, and the four variables are cache inputs, so setting them invalidates the entry).
// CI decodes KEYSTORE_BASE64 to a temp path and exports these four from
// repository secrets (see .github/workflows/release.yml and docs/release-signing.md).
fun env(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
val envKeystorePath = env("KEYSTORE_PATH")
val envKeystorePassword = env("KEYSTORE_PASSWORD")
val envKeyAlias = env("KEY_ALIAS")
val envKeyPassword = env("KEY_PASSWORD")
val releaseSigningConfigured =
    envKeystorePath != null && envKeystorePassword != null && envKeyAlias != null && envKeyPassword != null

if (!releaseSigningConfigured) {
    val touchesRelease = gradle.startParameter.taskNames.any { name ->
        name.contains("release", ignoreCase = true) ||
            name.substringAfterLast(':') in setOf("assemble", "build", "bundle")
    }
    if (touchesRelease) {
        logger.warn(
            "WARNING: release signing not configured (KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, " +
                "KEY_PASSWORD must all be set); the release build type stays unsigned."
        )
    }
}

android {
    namespace = "com.myapp"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.myapp"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                val keystore = rootDir.resolve(envKeystorePath!!)
                if (!keystore.isFile) {
                    throw GradleException("KEYSTORE_PATH points to a missing file: ${keystore.absolutePath}")
                }
                storeFile = keystore
                storePassword = envKeystorePassword
                keyAlias = envKeyAlias
                keyPassword = envKeyPassword
            }
        }
    }

    buildTypes {
        // SUBMIT_SWAPS guards the one call that moves money (POST /execute). Debug builds
        // stop after signing; only a release build submits. T6 owns signing and CI.
        debug {
            buildConfigField("boolean", "SUBMIT_SWAPS", "false")
        }
        release {
            buildConfigField("boolean", "SUBMIT_SWAPS", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseSigningConfigured) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.mobile.wallet.adapter.clientlib.ktx)
    implementation(libs.multimult)
    implementation(libs.web3.solana)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
