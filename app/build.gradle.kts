plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fyr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fyr"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // Credentials never live in this repo: they arrive as Gradle
        // properties — -PFYR_KEYSTORE=/path/release.jks plus the three
        // password properties, normally in ~/.gradle/gradle.properties. A
        // build without them is simply unsigned rather than failing, so the
        // release *compilation* can always be exercised; it just cannot be
        // installed or uploaded until the machine that owns the key asks for
        // it. See release below.
        create("release") {
            storeFile = file(
                providers.gradleProperty("FYR_KEYSTORE")
                    .getOrElse("${System.getProperty("user.home")}/.config/fyr/release.jks"),
            )
            storePassword = providers.gradleProperty("FYR_STORE_PASSWORD").orNull
            keyAlias = providers.gradleProperty("FYR_KEY_ALIAS").orNull
            keyPassword = providers.gradleProperty("FYR_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            // A release that ships debug-grade bytes is not a release: no
            // shrinking, no obfuscation, no dead-code removal, full readable
            // metadata for a decompiler. This codebase is R8-ready — no
            // reflection, no reflective serialization, org.json is the
            // platform's own, and manifest-referenced components keep
            // themselves — so the cost of turning it on was one verified
            // assembleRelease rather than a set of keep rules.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only when the keystore properties are actually present — an
            // assigned signingConfig with no key fails packaging, and the
            // ability to build (and lint) a release on any machine matters
            // more than the file's name.
            if (providers.gradleProperty("FYR_STORE_PASSWORD").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        // Generated for VERSION_NAME alone, so the About card reads the
        // version out of the build rather than restating it as a literal
        // that drifts the first time versionName is bumped.
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// ── the local-only promise, enforced ─────────────────────────────────────
// The About card promises "no account · no tracking", and the manifest's
// permission list is what makes that checkable rather than aspirational — but
// only until some dependency decides to merge in a permission of its own,
// silently, which is exactly how local-only apps stop being local-only.
//
// So this reads the *merged* manifest — where library declarations land — and
// insists on the exact set Fyr means to ship: VIBRATE for the tab tick,
// INTERNET for the one version check the launch gate makes, and the
// signature-level receiver guard AndroidX adds on Fyr's behalf. Nothing else.
// Exact rather than a blocklist, because a blocklist only catches the names
// already thought of; here, a permission nobody has considered fails the build
// and has to be argued for in this file. Cheap, and it cannot be forgotten.

/** Every permission Fyr ships, and the only ones this build will allow. */
val EXPECTED_PERMISSIONS = setOf(
    "android.permission.VIBRATE",
    // The one deliberate exception: the launch gate's version GET.
    "android.permission.INTERNET",
    // Not a choice — AndroidX declares this itself, signature level, so the
    // date-change receiver Fyr registers can be RECEIVER_NOT_EXPORTED on
    // every API level. It grants the app only to itself, and it is the
    // thing keeping that receiver shut to the rest of the phone.
    "com.fyr.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

tasks.register("verifyPermissionManifest") {
    group = "verification"
    description = "Fails if the merged manifest's permissions drift from the exact set Fyr ships."
    dependsOn("processDebugMainManifest")
    doLast {
        val manifest = layout.buildDirectory
            .file("intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml")
            .get()
            .asFile
        val text = manifest.readText()

        val declared = Regex("""<uses-permission(?:-sdk-\d+)?[^>]*?android:name="([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()
        val missing = EXPECTED_PERMISSIONS - declared
        val unexpected = declared - EXPECTED_PERMISSIONS

        check(missing.isEmpty() && unexpected.isEmpty()) {
            buildString {
                appendLine("The merged manifest's permissions no longer match what Fyr ships.")
                if (missing.isNotEmpty()) appendLine("  missing: $missing")
                if (unexpected.isNotEmpty()) {
                    appendLine("  unexpected: $unexpected")
                    appendLine(
                        "  If one of these is deliberate, add it to EXPECTED_PERMISSIONS in " +
                            "app/build.gradle.kts and say why in the manifest — an unaccounted-for " +
                            "permission on an app that advertises no tracking is a bug, not a detail.",
                    )
                }
            }
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("verifyPermissionManifest")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // The parts of this app most likely to rot in silence are the ones
    // nobody looks at: streak arithmetic and the document codecs. Both are
    // pure JVM code, so both are one plain unit test away from being unable
    // to regress unnoticed.
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    // org.json ships with the platform, and the platform's jar in a local
    // unit test is a stub that throws "not mocked" — so the codecs are
    // exercised against the real reference implementation instead.
    testImplementation("org.json:json:20231013")
}
