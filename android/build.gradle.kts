// AGP 9 bringt die Kotlin-Unterstützung mit — ein separates
// org.jetbrains.kotlin.android kollidiert mit der eingebauten `kotlin`-Extension.
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Signierung aus keystore.properties im Projektwurzelverzeichnis (nicht
// eingecheckt). Fehlt sie, bleibt der Release unsigniert, statt den Build zu
// brechen.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "com.inktest"
    compileSdk = 36

    defaultConfig {
        // Der Kotlin-Namespace bleibt beim Prototyp-Namen (unsichtbar für
        // Nutzer); die applicationId ist die dauerhafte Identität der App.
        applicationId = "de.bgghome.skribo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            // Gleicher Schlüssel wie der Release, damit adb install -r über
            // Store-Stände hinweg funktioniert.
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Plattformfreier Kern (Modell, Schema, Sync) — geteilt mit dem Desktop-Client.
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Motion prediction (MotionEventPredictor).
    implementation(libs.androidx.input.motionprediction)

    // WebDAV sync (HTTP client).
    implementation(libs.okhttp)
}
