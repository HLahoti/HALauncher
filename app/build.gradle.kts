import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.baselineprofile)
}

// Release signing credentials live in keystore.properties at the repo root (git-ignored).
// When it's absent (e.g. a fresh clone or CI without secrets), release builds stay unsigned
// rather than failing configuration.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.halauncher"
    compileSdk {
        version = release(37)
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.harshlahoti.halauncher"
        minSdk = 24
        targetSdk = 37
        versionCode = 7
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8: shrinks, optimises and obfuscates code and removes unused resources.
            // Keep rules live in src/main/keepRules.
            optimization {
                enable = true
            }
            // Sign with the release key when keystore.properties is present.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME, shown in Settings > About.
        buildConfig = true
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
    // Evaluates expressions typed into search so results can include a calculator answer.
    implementation(libs.exp4j)
    // Installs the baseline profile (src/release/generated/baselineProfiles) when sideloaded,
    // so startup and first interactions run precompiled code instead of interpreted code.
    implementation(libs.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}