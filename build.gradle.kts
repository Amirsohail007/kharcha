plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 compiles Kotlin itself; this pins the Kotlin version and adds the Compose compiler.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
