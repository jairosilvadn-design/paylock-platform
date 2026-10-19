plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Assinatura do APK release: lida de variáveis de ambiente para que o GitHub
// Actions (ver .github/workflows/build-apk.yml) consiga assinar o APK sem
// qualquer intervenção manual (sem Android Studio). Se essas variáveis não
// existirem (ex: build local de desenvolvimento), o release cai de volta
// para a assinatura "debug" padrão do Android — útil só para testar no
// emulador, nunca para instalar em aparelho de cliente real.
val keystorePath = System.getenv("PAYLOCK_KEYSTORE_PATH")
val hasReleaseSigning = !keystorePath.isNullOrBlank()

android {
    namespace = "com.paylock.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.paylock.agent"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("PAYLOCK_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("PAYLOCK_VERSION_NAME") ?: "1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("PAYLOCK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PAYLOCK_KEY_ALIAS")
                keyPassword = System.getenv("PAYLOCK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // Firebase (via BoM — mantém versões consistentes)
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
}
