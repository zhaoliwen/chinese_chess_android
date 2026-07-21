plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 从项目根目录 keystore.env 读取签名配置（该文件不进 git）
fun loadKeystoreEnv(file: java.io.File): Map<String, String> {
    require(file.exists()) {
        "缺少签名配置文件: ${file.absolutePath}，请复制 keystore.env.example 为 keystore.env 并填写"
    }
    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line ->
            val idx = line.indexOf('=')
            require(idx > 0) { "keystore.env 格式错误: $line" }
            line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
}

val keystoreEnv = loadKeystoreEnv(rootProject.file("keystore.env"))

android {
    namespace = "livan.chinese_chess"
    // 本地 SDK 无 36.0.0（AGP 默认版本且拉取失败），使用已安装的 37.0.0
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "livan.chinese_chess"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreEnv.getValue("KEYSTORE_FILE"))
            storePassword = keystoreEnv.getValue("KEYSTORE_PASSWORD")
            keyAlias = keystoreEnv.getValue("KEY_ALIAS")
            keyPassword = keystoreEnv.getValue("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}