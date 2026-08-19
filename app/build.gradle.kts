// AGP 9 bringt die Kotlin-Unterstützung mit — ein separates
// org.jetbrains.kotlin.android kollidiert mit der eingebauten `kotlin`-Extension.
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.inktest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.inktest"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
        getByName("release") {
            isMinifyEnabled = false
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Motion prediction (MotionEventPredictor).
    implementation(libs.androidx.input.motionprediction)

    // WebDAV sync (HTTP client).
    implementation(libs.okhttp)
}
