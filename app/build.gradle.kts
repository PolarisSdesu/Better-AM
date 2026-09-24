plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose") version "1.12.0"
}

android {
    namespace = "moe.polariss.betteram"
    compileSdk = 37

    defaultConfig {
        applicationId = "moe.polariss.betteram"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures {
        compose = true
    }

    bundle {
        language {
            // The manager switches locale at runtime through
            // AppSettings.localizedContext, so every translation has to ship in
            // the base APK instead of a Play language split.
            enableSplit = false
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1"
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    val abiSplits = providers.gradleProperty("abiSplits").orNull == "true"
    if (abiSplits) {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a")
                isUniversalApk = true
            }
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("androidx.navigation3:navigation3-runtime:1.1.7")
    implementation("androidx.navigation3:navigation3-ui:1.1.7")
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    // Keep the manager app bar behavior identical to Shizuku's MaterialToolbar,
    // including native action-menu overflow and long-press tooltips.
    implementation("com.google.android.material:material:1.14.0")

    implementation("org.jetbrains.compose.foundation:foundation:1.12.0")
    implementation("org.jetbrains.compose.ui:ui:1.12.0")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
}
