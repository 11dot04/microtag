plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.microtag"
    // The precise 37.1 minor API level (same as B-72)
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.microtag"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 1. signingConfigs MUST come BEFORE buildTypes
    signingConfigs {
        getByName("debug") {
            val localKeystore = rootProject.file("keystore/signing.jks")
            if (localKeystore.exists()) {
                storeFile = localKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }

        // Define releaseKey so the reference below doesn't crash
        create("releaseKey") {
            val localKeystore = rootProject.file("keystore/signing.jks")
            if (localKeystore.exists()) {
                storeFile = localKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    // 2. buildTypes comes AFTER signingConfigs
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("releaseKey")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    // aidl is for Shizuku; the two blocks that used to be separate are one now
    buildFeatures {
        compose = true
        aidl = true
    }

    // AGP 9 compiles Kotlin itself: no kotlin-android plugin, no kotlinOptions, no composeOptions.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val compose = "1.13.0-beta02"
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    // LocalLifecycleOwner now lives here (see ui/Permissions.kt)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")

    // Jetpack Compose, explicit versions (no BOM), matching B-72
    implementation("androidx.compose.ui:ui:$compose")
    implementation("androidx.compose.ui:ui-graphics:$compose")
    implementation("androidx.compose.foundation:foundation:$compose")
    implementation("androidx.compose.animation:animation:$compose")
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.graphics:graphics-shapes:1.1.0")

    // Shizuku Privileged API
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // ML Kit
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
