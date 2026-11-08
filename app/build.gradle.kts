import java.io.File
import java.util.Properties

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
        versionCode = 6
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Type-safe Build Configuration & Secrets
        buildConfigField("String", "COOKIE_NAME", "\"airslice_session\"")
        buildConfigField("int", "DEFAULT_SERVER_PORT", "8080")
        buildConfigField("String", "CPU_WAKELOCK_TAG", "\"AirSlice:ServerCpuWakeLock\"")
        buildConfigField("String", "WIFI_LOCK_TAG", "\"AirSlice:ServerWifiLock\"")
        buildConfigField("String", "NOTIFICATION_CHANNEL_ID", "\"airslice_server_channel\"")
        buildConfigField("int", "NOTIFICATION_ID", "1001")
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?: project.file("keystore.properties").takeIf { it.exists() }
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile != null) {
            load(keystorePropertiesFile.inputStream())
        }
    }

    fun resolveKeystoreFile(): File? {
        val envPath = System.getenv("KEYSTORE_FILE")
        if (!envPath.isNullOrEmpty()) {
            val f = file(envPath)
            if (f.exists()) return f
        }
        val propPath = keystoreProperties.getProperty("storeFile")
        if (!propPath.isNullOrEmpty()) {
            val f = file(propPath)
            if (f.exists()) return f
            val rf = rootProject.file(propPath)
            if (rf.exists()) return rf
        }
        val defaultFile = file("release.keystore")
        return if (defaultFile.exists()) defaultFile else null
    }

    signingConfigs {
        create("release") {
            val keystoreFile = resolveKeystoreFile()
            if (keystoreFile != null) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: keystoreProperties.getProperty("storePassword")
                    ?: ""
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: keystoreProperties.getProperty("keyAlias")
                    ?: ""
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: keystoreProperties.getProperty("keyPassword")
                    ?: ""
                enableV1Signing = true
                enableV2Signing = true
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
            val keystoreFile = resolveKeystoreFile()
            signingConfig = if (keystoreFile != null) {
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

    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
