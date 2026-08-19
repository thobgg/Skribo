// Plugin-Versionen werden hier einmal zentral aufgelöst und in den Modulen nur
// noch angewandt — AGP 9 bringt Kotlin selbst auf den Classpath, eine zweite
// Versionsangabe im Untermodul führt sonst zu einem Auflösungskonflikt.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
