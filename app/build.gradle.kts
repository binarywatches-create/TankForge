plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.tankforge.game"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    defaultConfig {
        applicationId = "com.tankforge.game"
        minSdk = 23
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.0"
    }
}
