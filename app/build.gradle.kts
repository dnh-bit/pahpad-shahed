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
        // بیلد تست با شناسهٔ جدا نصب می‌شود تا نسخهٔ اصلی روی گوشی جای آن را نگیرد
        val moddedBuild = (project.findProperty("modded") as String?)?.toBoolean() ?: false
        applicationId = if (moddedBuild) "ir.shahed.pahpad.mod" else "ir.shahed.pahpad"
        minSdk = 24
        targetSdk = 34
        versionCode = 18
        versionName = "0.1.8"
        resourceConfigurations += listOf("fa")

        // حالت تست: همهٔ مراحل و پهبادها باز. فقط با
        //   -Pmodded=true
        // فعال می‌شود؛ بیلد عادی هیچ تغییری نمی‌کند.
        buildConfigField("boolean", "MODDED", moddedBuild.toString())
    }
    buildFeatures { buildConfig = true }
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
    // تست‌های رندر باید به assets واقعی (بافت‌ها و فونت) دسترسی داشته باشند
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
// Native Android Canvas only; ad SDK = Tapsell Plus (mavenCentral)
dependencies {
    implementation("ir.tapsell.plus:tapsell-plus-sdk-android:2.3.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
