plugins {
    id("com.android.application")
}

android {
    namespace = "it.battito.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.battito.game"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-prototipo"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Prototipo: firmato con la chiave di debug così l'APK release si installa subito.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
