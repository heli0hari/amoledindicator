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
            // R8 would strip the module entry class. Keep it off.
            isMinifyEnabled = false
        }
    }

    // Keep only our module descriptor files from Java resources.
    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
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
    // Modern Xposed API, provided by Vector/LSPosed at runtime. Must stay compileOnly.
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("androidx.palette:palette:1.0.0")
}
