plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.artisanai"
    compileSdk = 35

    defaultConfig {
        // 3.0 起换独立包名：与旧版 2.0（com.artisanai.v2）共存，不再要求卸载旧版。
        // 版本管理约定：
        //   · 同一包名内靠固定签名密钥（GitHub Secrets）支持覆盖升级，3.0.x 之间可直接升级；
        //   · versionCode = CI run_number，单调递增，永不回退；
        //   · versionName = 3.0.<run_number>，与构建号一一对应，便于排查。
        applicationId = "com.artisanai.v3"
        minSdk = 26
        targetSdk = 35
        val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
        versionCode = buildNumber
        versionName = "3.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API Key injected from GitHub Secrets or local.properties
        val apiKey = project.findProperty("APIYI_KEY") as String? ?: ""
        buildConfigField("String", "APIYI_KEY", "\"$apiKey\"")
    }

    // 固定签名：CI 通过环境变量注入密钥库（来自 GitHub Secrets，解码到临时文件）。
    // 缺失时回退到 debug 签名，保证本地 / fork 无密钥也能编译。
    val releaseKeystore = System.getenv("ARTISAN_KEYSTORE_FILE")
    val hasReleaseSigning = !releaseKeystore.isNullOrBlank() && file(releaseKeystore).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("ARTISAN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ARTISAN_KEY_ALIAS")
                keyPassword = System.getenv("ARTISAN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning)
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            // debug 包也用固定密钥签名，这样无论装 debug 还是 release，后续都能覆盖升级
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.window.size.cls)
    debugImplementation(libs.androidx.ui.tooling)
}
