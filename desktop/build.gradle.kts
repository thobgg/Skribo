import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// Planungs-Client für Windows 11 / macOS / Linux — eine Codebasis, native
// Pakete via jpackage. Teilt sich mit dem Board-Client den Kern in :shared.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Auf Android kommt org.json aus der Plattform, auf der JVM nicht.
    implementation(libs.json)

    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.inktest.desktop.MainKt"

        nativeDistributions {
            // Linux ist die primäre Plattform (eigener Arbeitsplatz), Windows und
            // macOS für das Kollegium.
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "Skribo"
            packageVersion = "1.0.0"
            description = "Unterrichtsplanung — Skribo Desktop"
            vendor = "Skribo"

            linux {
                menuGroup = "Education"
            }
        }
    }
}
