plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            keyAlias = System.getenv("keyAlias")
            storePassword = System.getenv("storePassword")
            keyPassword = System.getenv("keyPassword")
        }
    }
    namespace = "pokego.pip"
    compileSdk = 35

    defaultConfig {
        applicationId = "pokego.pip"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        resources {
            excludes.add("**/kotlin/**")
            excludes.add("kotlin-tooling-metadata.json")
        }
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}