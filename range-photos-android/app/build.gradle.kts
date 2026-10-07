plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.rangephotos"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.rangephotos"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "0.17.0"

        // Téléphones ARM uniquement (tous les téléphones actuels) : APK plus léger.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Le modèle de reconnaissance de visages est lu tel quel depuis l'APK.
    androidResources {
        noCompress += "onnx"
    }

    // Clé de signature partagée et publique (app/debug.keystore) : tous les APK compilés,
    // sur n'importe quelle machine, ont la même signature et se réinstallent par-dessus
    // la version précédente sans rien perdre.
    signingConfigs {
        create("shared") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Détection de visages 100 % sur le téléphone (modèle inclus dans l'appli)
    implementation("com.google.mlkit:face-detection:16.1.7")

    // Dossiers personnalisés : codes-barres et sujets (voitures, animaux…), modèles embarqués, 100 % sur le téléphone
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:image-labeling:17.0.9")

    // Reconnaissance des personnes : modèle SFace exécuté sur le téléphone (voir THIRD_PARTY_NOTICES.md)
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.mockito:mockito-core:5.12.0")
}
