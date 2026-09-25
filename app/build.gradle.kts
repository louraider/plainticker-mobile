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
    namespace = "com.plainticker.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.plainticker.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sign in with Google (docs/google-sign-in.md): the WEB OAuth client id, passed to Credential
        // Manager as the server client id so the ID token's audience is the one
        // POST /api/v1/auth/google verifies. Public by design, not a secret.
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"256856154538-vk2k3l1ug7g1ud0dbe2jdiff740juemf.apps.googleusercontent.com\"",
        )
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
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.googleid)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
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

// ---- R8 serialization audit -----------------------------------------------------------------
// A JVM unit test compiles and runs unminified, so it cannot see what R8 did to a generated
// kotlinx.serialization serializer; the release-only break on Detail's "the read" section (a 200
// already logged server-side, nothing rendered on the device) is exactly the class of bug no
// existing unit test could have caught. This task is what can: it reads
// app/build/outputs/mapping/release/mapping.txt, the same file the audit for that bug read by
// hand, and confirms each listed model's `$$serializer` still carries a working
// deserialize(Decoder)/serialize(Encoder, T) pair and its companion still carries `serializer()`.
// It does not run the serializer against a live body (that needs a full classpath and a real
// device, which is what the original bug needed to be found at all) — it confirms R8 left the
// scaffolding a working release needs in place, which is the layer this task's own investigation
// had to establish by hand from mapping.txt, usage.txt and configuration.txt.
val auditReleaseSerializers = tasks.register("auditReleaseSerializers") {
    group = "verification"
    description = "Fails the release build if R8 stripped a wire model's kotlinx.serialization " +
        "companion or \$\$serializer (see app/proguard-rules.pro)."
    dependsOn("minifyReleaseWithR8")

    val mappingFile = layout.buildDirectory.file("outputs/mapping/release/mapping.txt")
    inputs.file(mappingFile)
    val reportFile = layout.buildDirectory.file("outputs/mapping/release/serializer-audit.txt")
    outputs.file(reportFile)

    // Every model this app decodes straight from a live server response into a screen a reader
    // can see nothing else for if the decode silently fails: task A6's read payload and its two
    // children, alongside the summary and next-up rows the audit compared them against because
    // those are confirmed working on a device today.
    val mustDecode = listOf(
        "com.plainticker.mobile.data.plainticker.TickerReadResponse",
        "com.plainticker.mobile.data.plainticker.NarrativeRead",
        "com.plainticker.mobile.data.plainticker.NextStepsRead",
        "com.plainticker.mobile.data.plainticker.SummaryResponse",
        "com.plainticker.mobile.data.plainticker.SummaryRow",
        "com.plainticker.mobile.data.plainticker.NextUpRow",
        // Sign in with Google: a stripped serializer here would sign a person in server side and
        // leave the app unable to read who it signed in as.
        "com.plainticker.mobile.data.auth.GoogleAuthResponse",
        "com.plainticker.mobile.data.auth.GoogleAuthUser",
    )

    doLast {
        // mapping.txt lists one unindented "original.Name -> obfuscated:" line per class,
        // followed by its indented member lines; group members under the top-level name that
        // most recently preceded them so each fully-qualified name below can be checked in
        // isolation from every other class's identically-shaped member lines.
        val blocks = LinkedHashMap<String, StringBuilder>()
        var current: StringBuilder? = null
        mappingFile.get().asFile.forEachLine { line ->
            current = if (line.isNotEmpty() && !line[0].isWhitespace() && " -> " in line) {
                StringBuilder().also { blocks[line.substringBefore(" -> ")] = it }
            } else {
                current?.apply { appendLine(line) }
            }
        }

        val failures = mustDecode.filterNot { fqcn ->
            val serializer = blocks["$fqcn\$\$serializer"]?.toString().orEmpty()
            val companion = blocks["$fqcn\$Companion"]?.toString().orEmpty()
            val quoted = Regex.escape(fqcn)
            Regex("""$quoted deserialize\(kotlinx\.serialization\.encoding\.Decoder\)""").containsMatchIn(serializer) &&
                Regex("""serialize\(kotlinx\.serialization\.encoding\.Encoder,$quoted\)""").containsMatchIn(serializer) &&
                "serializer():" in companion
        }

        val report = reportFile.get().asFile
        report.parentFile.mkdirs()
        if (failures.isNotEmpty()) {
            report.writeText("FAILED: ${failures.joinToString(", ")}\n")
            throw GradleException(
                "R8 stripped the kotlinx.serialization scaffolding release needs to decode: " +
                    failures.joinToString(", ") + ". Compare mapping.txt for one of these " +
                    "against a class still known to work, and check app/proguard-rules.pro.",
            )
        }
        report.writeText("OK: ${mustDecode.joinToString(", ")}\n")
    }
}

// AGP registers `assembleRelease` lazily, after this script's top-level statements run, so the
// hook has to wait for it rather than look it up by name directly.
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(auditReleaseSerializers) }
