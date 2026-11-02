plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.anasdev.fluxlink"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.anasdev.fluxlink"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Type-safe Build Configuration & Secrets
        buildConfigField("String", "COOKIE_NAME", "\"airslice_session\"")
        buildConfigField("int", "DEFAULT_SERVER_PORT", "8080")
        buildConfigField("String", "CPU_WAKELOCK_TAG", "\"AirSlice:ServerCpuWakeLock\"")
        buildConfigField("String", "WIFI_LOCK_TAG", "\"AirSlice:ServerWifiLock\"")
        buildConfigField("String", "NOTIFICATION_CHANNEL_ID", "\"airslice_server_channel\"")
        buildConfigField("int", "NOTIFICATION_ID", "1001")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_FILE") ?: "release.keystore"
            val keystoreFile = file(keystorePath)
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "AirSlice")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val keystorePath = System.getenv("KEYSTORE_FILE") ?: "release.keystore"
            val keystoreFile = file(keystorePath)
            signingConfig = if (keystoreFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "src/main/keepRules/rules.keep"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
