plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rohit.mangainker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rohit.mangainker"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0-alpha"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    testOptions { unitTests.isReturnDefaultValues = true }

    lint {
        // API 36 is the latest stable Android platform; API 37 remains in beta.
        disable += "OldTargetApi"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    testImplementation("junit:junit:4.13.2")
}
