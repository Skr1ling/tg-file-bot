plugins {
    id("com.android.application")
}

android {
    namespace = "com.skriling.ierichonia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.skriling.ierichonia"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-vs01"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
