import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cleanbeam.remote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cleanbeam.remote"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes from a properties file outside the repo (storeFile, storePassword,
    // keyAlias, keyPassword): $CLEANBEAM_SIGNING, or ~/.cleanbeam/release-signing.properties.
    // Without it a release build is signed with the debug key, so anyone can still build one.
    val releaseSigning = File(System.getenv("CLEANBEAM_SIGNING")
        ?: "${System.getProperty("user.home")}/.cleanbeam/release-signing.properties")
    signingConfigs {
        if (releaseSigning.isFile) create("release") {
            val p = Properties()
            FileInputStream(releaseSigning).use { stream -> p.load(stream) }
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
