// AppForge - offline app builder module (:builder).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Same stable CI keystore as the other modules, so every APK keeps one signature.
val stableKeystore = rootProject.file("ci-keystore/release.p12")

android {
    namespace = "com.universalrp.appforge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.universalrp.appforge"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        if (stableKeystore.exists()) {
            create("stable") {
                storeFile = stableKeystore
                storePassword = "cleansweep-ci"
                keyAlias = "cleansweep"
                keyPassword = "cleansweep-ci"
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (stableKeystore.exists()) signingConfig = signingConfigs.getByName("stable")
        }
        debug {
            if (stableKeystore.exists()) signingConfig = signingConfigs.getByName("stable")
        }
    }

    buildFeatures {
        compose = true
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
