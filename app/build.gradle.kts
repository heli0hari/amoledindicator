plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.siva.pinlight"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.siva.pinlight"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // R8 would strip the Xposed entry class. Keep it off.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Provided by LSPosed at runtime. Must stay compileOnly.
    compileOnly("de.robv.android.xposed:api:82")
    implementation("androidx.palette:palette:1.0.0")
}
