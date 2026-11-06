plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildVersionCode = providers.gradleProperty("VERSION_CODE").orNull?.toIntOrNull() ?: 4
val buildVersionName = providers.gradleProperty("VERSION_NAME").orNull ?: "1.1.0-alpha"

android {
    namespace = "com.example.mangainker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rohit.mangainker"
        minSdk = 26
        targetSdk = 36
        versionCode = buildVersionCode
        versionName = buildVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStoreFile = providers.environmentVariable("MANGA_INKER_RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.environmentVariable("MANGA_INKER_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.environmentVariable("MANGA_INKER_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("MANGA_INKER_RELEASE_KEY_PASSWORD").orNull
    val releaseSigningConfigured = !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Keep the test build side-by-side with any installed release APK.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            // Without protected CI secrets Gradle produces an unsigned release APK.
            // A distributable APK is signed only when the owner supplies the keystore secrets.
            signingConfig = if (releaseSigningConfigured) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
