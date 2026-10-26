import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cleanbeam.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cleanbeam.launcher"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // Two editions of the same launcher. Lite has no catalogue, no network and no Ambient theme;
    // the code seam is the per-flavor `Edition` object under src/<flavor>/java.
    flavorDimensions += "edition"
    productFlavors {
        create("full") { dimension = "edition"; isDefault = true }
        create("lite") { dimension = "edition"; versionNameSuffix = "-lite" }
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
            // R8 shrinks and optimises the dex: less code to load and verify on a cold start,
            // smaller .dex/.oat mappings in memory on a 1 GB box.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.palette:palette-ktx:1.0.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
