plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.example.halauncher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.halauncher"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8: shrinks, optimises and obfuscates code and removes unused resources.
            // Keep rules live in src/main/keepRules.
            optimization {
                enable = true
            }
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// The baseline profile plugin records the profile on a "nonMinifiedRelease" build, so it names
// readable classes that R8 then maps onto the optimised release build. The plugin copies the
// release build type (optimization on) and predates the optimization block, so it's turned off
// here, after the plugin has set that build type up.
androidComponents {
    finalizeDsl { android ->
        android.buildTypes.findByName("nonMinifiedRelease")?.optimization?.enable = false
    }
}

dependencies {
    implementation(libs.core)
    implementation(libs.core.ktx)
    implementation(libs.activity)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    // Installs the baseline profile (src/release/generated/baselineProfiles) when sideloaded,
    // so startup and first interactions run precompiled code instead of interpreted code.
    implementation(libs.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}