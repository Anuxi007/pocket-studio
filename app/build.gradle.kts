plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.anuxi.pocketstudio"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.anuxi.pocketstudio"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "0.0.2"
    }

    // Un APK por arquitectura para que pesen menos. Casi todos los celulares
    // actuales usan arm64-v8a.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    // La llave de firma de la alpha vive en /signing. Si defines los secretos
    // PS_KEYSTORE, PS_KS_PASS, PS_KEY_ALIAS y PS_KEY_PASS, se usan en su lugar.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("PS_KEYSTORE") ?: "../signing/alpha.jks")
            storePassword = System.getenv("PS_KS_PASS") ?: "pocketstudio"
            keyAlias = System.getenv("PS_KEY_ALIAS") ?: "alpha"
            keyPassword = System.getenv("PS_KEY_PASS") ?: "pocketstudio"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    // yt-dlp, Python y FFmpeg viajan como librerias nativas que se extraen al instalar.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val youtubedl = "0.17.4"
    implementation("io.github.junkfood02.youtubedl-android:library:$youtubedl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$youtubedl")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.documentfile:documentfile:1.0.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("io.coil-kt:coil-compose:2.6.0")
}
