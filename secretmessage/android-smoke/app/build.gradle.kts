plugins {
    id("com.android.application") version "8.13.0"
}

val lanTestEnabled = providers.gradleProperty("lanTestEnabled").orNull == "true"

android {
    namespace = "com.rpideas.secretmessage.smoke"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rpideas.secretmessage.smoke"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.1-lan-test"
        buildConfigField("boolean", "LAN_TEST_ENABLED", lanTestEnabled.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
