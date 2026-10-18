plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.manu.carnetrando"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.manu.carnetrando"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.6"
    }

    // Même clé de signature publique que Mes Photos : une nouvelle version s'installe par-dessus l'ancienne.
    signingConfigs {
        create("shared") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("shared") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
