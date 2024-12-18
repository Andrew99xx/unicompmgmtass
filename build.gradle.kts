plugins {
    kotlin("multiplatform") version "1.9.0" // Latest stable Kotlin version
    id("org.jetbrains.compose") version "1.5.10" // Consistent Compose version

}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17" // Use "11" or "1.8" if you need compatibility with older JVM versions
            }
        }
    }
    sourceSets {
        val jvmMain by getting {
            kotlin.srcDirs("src/main/kotlin") // Point to your custom directory
            dependencies {
                implementation(compose.desktop.currentOs) // Compose Desktop library with MaterialTheme
                implementation("org.jetbrains.compose.ui:ui:1.5.0") // Compose UI for Desktop
                implementation("org.jetbrains.compose.foundation:foundation:1.5.0") // Compose Foundation
                implementation("org.jetbrains.compose.material:material:1.5.0") // Material library for Desktop
                implementation("org.xerial:sqlite-jdbc:3.43.0.0") // SQLite JDBC driver
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}