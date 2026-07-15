plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cashfteam.resonance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cashfteam.resonance"
        minSdk = 26
        targetSdk = 34

        // Bump versionCode by 1 for EVERY release you upload to a store.
        versionCode = 2
        versionName = "1.1"

        resourceConfigurations += listOf("fa", "en")
        vectorDrawables.useSupportLibrary = true
    }

    // --- Release signing ---
    // Values come from environment variables (set as GitHub Secrets in CI).
    // If they're missing, we simply skip signing so a plain build still works.
    val storeFileEnv = System.getenv("KEYSTORE_FILE")
    val storePasswordEnv = System.getenv("KEYSTORE_PASSWORD")
    val keyAliasEnv = System.getenv("KEY_ALIAS")
    val keyPasswordEnv = System.getenv("KEY_PASSWORD")
    val hasSigning = !storeFileEnv.isNullOrBlank() && !storePasswordEnv.isNullOrBlank() &&
            !keyAliasEnv.isNullOrBlank() && !keyPasswordEnv.isNullOrBlank()

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(storeFileEnv!!)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
}
