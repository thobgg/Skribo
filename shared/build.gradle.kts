// Plattformfreier Kern von Skribo: Datenmodell, On-Disk-/WebDAV-Schema und
// die Strich-Mathematik. Wird von :android und (ab M3) vom Desktop-Client
// geteilt, damit das Schema nur an EINER Stelle existiert.
//
// Bewusst eine schlichte Kotlin-JVM-Bibliothek statt Kotlin Multiplatform:
// beide Clients laufen auf der JVM, KMP würde nur Overhead bringen.
plugins {
    alias(libs.plugins.kotlin.jvm)
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
    // org.json ist auf Android Teil der Plattform (android.jar). Deshalb hier nur
    // compileOnly — sonst gäbe es beim Dexen doppelte Klassen. Der Desktop-Client
    // bindet die Bibliothek selbst als implementation ein.
    compileOnly(libs.json)

    // WebDAV-Sync.
    implementation(libs.okhttp)

    testImplementation(libs.json)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
