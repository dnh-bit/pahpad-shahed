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
        versionCode = 2
        versionName = "0.0.2"
        resourceConfigurations += listOf("fa")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // بازی به صورت کامل با Canvas و APIهای پایه اندروید نوشته شده
    // و به هیچ کتابخانه‌ی بیرونی نیازی ندارد.
}
