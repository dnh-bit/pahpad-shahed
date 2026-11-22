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
        // بیلد تستِ کنار نسخهٔ اصلی: فقط شناسه و اسم عوض می‌شود، قفل‌ها و
        // پیشرفت مثل نسخهٔ واقعی می‌مانند (برخلاف modded که همه‌چیز را باز می‌کند)
        //   -PtestBuild=true
        val testBuild = (project.findProperty("testBuild") as String?)?.toBoolean() ?: false
        applicationId = when {
            moddedBuild -> "ir.shahed.pahpad.mod"
            testBuild -> "ir.shahed.pahpad.test"
            else -> "ir.shahed.pahpad"
        }
        resValue("string", "app_name", if (testBuild) "پهپاد شاهد تست" else "پهپاد شاهد")
        minSdk = 24
        targetSdk = 34
        versionCode = 31
        versionName = "0.3.1"
        resourceConfigurations += listOf("fa")

        // حالت مود: همهٔ مراحل و پهبادها باز؛ فقط با
        //   -Pmodded=true
        // فعال می‌شود. بیلد عادی (از جمله بیلد رسمی گیت‌هاب) همیشه قفل است.
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
        val keystoreProps = Properties().apply { FileInputStream(keystorePropsFile).use { load(it) } }
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
    if (!keystorePropsFile.exists()) {
        // بیلد انتشارِ بدون امضا نباید ساکت بگذرد: APK بی‌امضا روی هیچ
        // گوشی‌ای نصب نمی‌شود و ممکن است اشتباهی جای نسخهٔ نهایی جا بخورد.
        // راه فرار صریح برای بیلد محلی: -PallowUnsignedRelease=true
        gradle.taskGraph.whenReady {
            val releaseRequested = allTasks.any {
                it.name.contains("Release") &&
                    (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package"))
            }
            val allowed = (project.findProperty("allowUnsignedRelease") as String?)?.toBoolean() == true
            if (releaseRequested && !allowed) throw GradleException(
                "Release build requires app/keystore.properties — or pass -PallowUnsignedRelease=true for a local unsigned build."
            )
        }
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
