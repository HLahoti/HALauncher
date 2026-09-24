plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.halauncher.baselineprofile"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Profile generation needs Android 9+.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    targetProjectPath = ":app"
}

// Generates on the attached phone: ./gradlew :app:generateBaselineProfile
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
