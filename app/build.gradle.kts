plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace="com.rebass.app"
    compileSdk=35
    defaultConfig {
        applicationId="com.rebass.app"
        minSdk=26
        targetSdk=35
        versionCode=2
        versionName="1.0"
        ndk {
            abiFilters.add("armeabi-v7a")
        }
    }
}
