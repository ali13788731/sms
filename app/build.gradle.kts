import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// اطلاعات امضای نسخه‌ی انتشار: از فایل keystore.properties (کنار build.gradle.kts ریشه)
// یا از متغیرهای محیطی (برای GitHub Actions) خوانده می‌شود. هیچ‌کدام در مخزن ذخیره نمی‌شوند.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signing(key: String, env: String): String? =
    keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signing("storeFile", "KEYSTORE_PATH")
val releaseStorePassword = signing("storePassword", "KEYSTORE_PASSWORD")
val releaseKeyAlias = signing("keyAlias", "KEY_ALIAS")
val releaseKeyPassword = signing("keyPassword", "KEY_PASSWORD")
val canSignRelease = releaseStoreFile != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "ir.smsforward"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.smsforward"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "2.3.0"

        // آدرس فایل update.json برای بررسی نسخه‌ی جدید (فقط HTTPS).
        // YOUR_ را با آدرس واقعی خودتان عوض کنید؛ تا آن موقع بررسی بروزرسانی خاموش است.
        // نمونه: https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/update.json
        buildConfigField(
            "String", "UPDATE_URL",
            "\"https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/update.json\""
        )
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    bundle {
        language { enableSplit = false }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
