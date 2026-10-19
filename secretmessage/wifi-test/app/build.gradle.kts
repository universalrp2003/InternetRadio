// Secret Message, the published app: the WhisperPad product screens from ../../product, compiled
// unchanged, plus the connection in this directory, which uses the mls-rs/AWS-LC session from
// ../../native-mls-rs through JNI, directly on the same Wi-Fi or over the Internet through the owner's
// relay (../relay). It grew from the owner-approved test builds of 17 October 2026 (same Wi-Fi, then
// the Internet relay); the owner then asked to publish it without an independent security review.
// The release APK is built unsigned here and signed in CI with the project's private key; debug
// builds use their own package (".debug") so they never collide with it. The offline preview
// (../../product) keeps its own build, permissions and checks.
plugins {
    id("com.android.application") version "8.13.0"
    id("org.jetbrains.kotlin.android") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}

val productDir = rootDir.resolve("../product")

android {
    // The product sources generate BuildConfig in this namespace; the application ID keeps this
    // test build separate from the offline preview, so both can be installed side by side.
    namespace = "com.rpideas.secretmessage.preview"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rpideas.secretmessage"
        minSdk = 26
        targetSdk = 35
        // Raise versionCode by one and versionName (1.x.y: y for fixes, x for new features) for every
        // published version, together with EXPECTED_VERSION/EXPECTED_CODE in
        // .github/workflows/secret-message-wifi-test.yml.
        versionCode = 2
        versionName = "1.1.0"
        manifestPlaceholders["appLabel"] = "Secret Message"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", productDir.resolve("src/main/java"))
            res.srcDirs("src/main/res", productDir.resolve("src/main/res"))
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Secret Message (debug)"
        }
        release {
            // Not shrunk, so JNI and OkHttp behave exactly as in the tested debug build.
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // The product's own lint gate runs in the preview workflow; here lint reports without blocking.
        abortOnError = false
        checkReleaseBuilds = false
        textReport = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // WebSocket client for the Internet relay; it adds no permissions (INTERNET is already declared).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
