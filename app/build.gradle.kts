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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // امضای release با keystore اختصاصی (فایل‌ها از CI تزریق می‌شوند)
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
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // بازی به صورت کامل با Canvas و APIهای پایه اندروید نوشته شده
    // و به هیچ کتابخانه‌ی بیرونی نیازی ندارد.
}
