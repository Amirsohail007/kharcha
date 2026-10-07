import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing comes from keystore.properties (gitignored). Without it, release builds are unsigned.
val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "com.amir.expense"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.amir.expense"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = rootProject.file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    packaging {
        // BouncyCastle's post-quantum tables (~7 MB); PDF decryption only needs AES/RC4.
        resources.excludes += "org/bouncycastle/pqc/**"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Opens the password-protected PhonePe statement and extracts its text.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // Google Drive backup: Play services sign-in for the Drive token, WorkManager for the daily sync.
    // Drive itself is called over plain HTTPS (sync/DriveApi.kt), not the Google API client library.
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in JVM tests; the backup codec tests need the real thing.
    testImplementation("org.json:json:20260814")
    // Real SQLite for MigrationTest.
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    // Desktop PDFBox: lets a JVM test read a real statement from samples/ without a phone.
    testImplementation("org.apache.pdfbox:pdfbox:2.0.37")
}
