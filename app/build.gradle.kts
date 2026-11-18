plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.giallotouch"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "it.giallotouch"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "0.6.1-prototipo"
        ndk {
            // CMF Phone 2 Pro e quasi tutti i telefoni moderni sono arm64.
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Prova dell'app intera sul computer (Robolectric): servono le risorse Android nei test.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    testImplementation("junit:junit:4.13.2")
    // Banco di prova dell'interfaccia: l'app vera su computer, con foto dello schermo (solo in locale).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
}

// Test facoltativi sul gioco vero: si attivano solo passando la ROM (che resta fuori dal repository),
// uno stato salvato e la libreria del motore compilata per il computer, per esempio:
// ./gradlew testDebugUnitTest -Pgiallo.rom=/percorso/gioco.gb -Pgiallo.state=/percorso/stato.state -Pgiallo.hostlib=/percorso/cartella
tasks.withType<Test>().configureEach {
    listOf("giallo.rom", "giallo.state", "giallo.state.yesno", "giallo.state.pallet", "giallo.zip", "giallo.state.text", "giallo.state.naming", "giallo.state.bag", "giallo.state.title", "giallo.shots", "giallo.battery", "giallo.state.squadra", "giallo.state.items").forEach { key ->
        providers.gradleProperty(key).orNull?.let { systemProperty(key, it) }
    }
    providers.gradleProperty("giallo.hostlib").orNull?.let { jvmArgs("-Djava.library.path=$it") }
    // Banco di prova dell'interfaccia: ogni prova in una macchina Java nuova (l'app vera lascia thread e stato dietro di sé).
    if (providers.gradleProperty("giallo.shots").isPresent) forkEvery = 1
}
