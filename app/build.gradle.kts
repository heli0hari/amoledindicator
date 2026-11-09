plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI passes these from GitHub secrets. Locally they're unset, so release
// builds fall back to the debug key.
val signingStore: String? = System.getenv("SIGNING_STORE_FILE")
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "dev.siva.pinlight"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.siva.pinlight"
        minSdk = 33
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.2.$buildNumber"
    }

    signingConfigs {
        create("release") {
            if (signingStore != null) {
                storeFile = file(signingStore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 would strip the module entry class. Keep it off.
            isMinifyEnabled = false
            signingConfig = if (signingStore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    lint {
        // Hidden-API window type and broad package queries are intentional.
        checkReleaseBuilds = false
        abortOnError = false
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
