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
