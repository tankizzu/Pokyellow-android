plugins {
    id("com.android.application")
}

// ── Numero di aggiornamento: +1 a ogni APK consegnato (vedi "Aggiornamenti" nel README) ──
val aggiornamento = 3
val versione = "0.$aggiornamento.0"

android {
    namespace = "it.battito.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.battito.game"
        minSdk = 26
        targetSdk = 36
        versionCode = aggiornamento
        versionName = versione
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Prototipo: firmato con la chiave di debug così l'APK release si installa subito.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Foto delle schermate sul computer (solo con -Pbattito.shots=/cartella)
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// Anteprima del brano sul computer: ./gradlew testDebugUnitTest -Pbattito.wav=/percorso/brano.wav
tasks.withType<Test>().configureEach {
    providers.gradleProperty("battito.wav").orNull?.let { systemProperty("battito.wav", it) }
    providers.gradleProperty("battito.shots").orNull?.let { systemProperty("battito.shots", it) }
    testLogging { showStandardStreams = true }
}

// Robolectric: l'immagine di Android la scarica Gradle (passa dal proxy), poi Robolectric lavora offline.
val robolectricAndroid: Configuration by configurations.creating
dependencies { robolectricAndroid("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7") }
val robolectricJars = tasks.register<Copy>("robolectricJars") {
    from(robolectricAndroid)
    into(layout.buildDirectory.dir("robolectric-jars"))
}
tasks.withType<Test>().configureEach {
    if (providers.gradleProperty("battito.shots").isPresent) {
        dependsOn(robolectricJars)
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-jars").get().asFile.absolutePath)
    }
}

// APK con il numero di aggiornamento nel nome: build/dist/Battito-agg3-v0.3.0.apk
val apkNumerato = tasks.register<Copy>("apkNumerato") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.buildDirectory.dir("dist"))
    rename { "Battito-agg$aggiornamento-v$versione.apk" }
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(apkNumerato) }
