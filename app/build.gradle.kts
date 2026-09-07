import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ir.shahed.pahpad"
    compileSdk = 34
    defaultConfig {
        applicationId = "ir.shahed.pahpad"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "0.0.4"
        resourceConfigurations += listOf("fa")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    val keystorePropsFile = rootProject.file("app/keystore.properties")
    if (keystorePropsFile.exists()) {
        val keystoreProps = Properties().apply { load(FileInputStream(keystorePropsFile)) }
        signingConfigs {
            create("upload") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
        buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("upload") } }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
// Native Android Canvas only; no new runtime dependency or network permission.
dependencies {}
