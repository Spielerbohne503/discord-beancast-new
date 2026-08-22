plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uk.spielerbohne.petodo.huelle"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.spielerbohne.petodo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ohne eigenen Schlüssel signiert das Debug-Zertifikat — die App wird
            // seitlich installiert, nicht über einen Laden verteilt.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        // Für `BuildConfig.VERSION_NAME` in den Einstellungen der Seite.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        getByName("main") {
            // Die Webseite wird **nicht** ins Android-Projekt kopiert und dort gepflegt.
            // Sie liegt einmal im Wurzelverzeichnis; der Bauschritt legt sie daneben.
            assets.srcDir(layout.buildDirectory.dir("generated/webassets"))
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

/**
 * Die Webseite ins Paket legen.
 *
 * Kopiert wird aus dem Wurzelverzeichnis des Projekts — es gibt genau **eine** Fassung der
 * Webseite. Eine zweite Kopie im Android-Ordner wäre nach dem ersten Wochenende
 * auseinandergelaufen, und dann suchte man den Fehler in der falschen Datei.
 */
val webseiteKopieren by tasks.registering(Copy::class) {
    val wurzel = rootProject.layout.projectDirectory.dir("..")

    from(wurzel) {
        include("index.html")
        include("icon.svg")
        include("manifest.webmanifest")
        include("src/**")
    }
    // Der Dienstarbeiter bleibt draußen: Im Paket liegen die Dateien schon, und eine
    // zweite zwischengespeicherte Kopie zeigt nach einem Update tagelang die alte Fassung.
    exclude("sw.js")

    into(layout.buildDirectory.dir("generated/webassets/web"))
}

/*
 * Alles, was den Asset-Ordner anfasst, muss auf die Kopie warten.
 *
 * Ohne diese Abhängigkeit baut Gradle beim ersten Lauf ein Paket ohne Webseite und beim
 * zweiten eines mit — der unangenehmste Fehler, den es gibt, weil er beim Nachbauen
 * verschwindet. Lint zählt dazu: Es liest dieselben Ordner.
 */
tasks.matching { aufgabe ->
    listOf("Assets", "Lint", "Package", "Bundle").any { aufgabe.name.contains(it) }
}.configureEach {
    dependsOn(webseiteKopieren)
}

dependencies {
    // Bewusst leer: keine Laufzeitabhängigkeiten. Die Hülle benutzt nur, was in Android
    // ohnehin steckt — WebView, AlarmManager, NotificationManager.
    testImplementation(kotlin("test"))
}
