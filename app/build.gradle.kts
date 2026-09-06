plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rebass.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rebass.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2"

        ndk {
            abiFilters.add("armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
