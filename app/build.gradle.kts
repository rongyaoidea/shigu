plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.shigu"
    compileSdk = libs.versions.compileSdk.get().toInt()

    val tagVersion = System.getenv("GITHUB_REF_NAME")
        ?.removePrefix("v")
        ?.takeIf { it.matches(Regex("\\d+(\\.\\d+){1,3}.*")) }

    fun versionCodeOf(v: String?): Int? {
        if (v.isNullOrBlank()) return null
        val parts = v.split(".", "-", "+").take(3).mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10000 + minor * 100 + patch
    }

    defaultConfig {
        applicationId = "com.shigu"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // tag 发布：v0.2.0 → versionName 0.2.0，versionCode 200；单调递增，避免 run number 回退。
        versionName = tagVersion ?: "0.10.0"
        versionCode = versionCodeOf(tagVersion) ?: 1000
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }

        // 本地/CI 均从环境变量读取；GitHub Actions 在 tag 构建时注入 Secrets。
        val releaseStore = System.getenv("KEYSTORE_FILE")
        val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
        val releaseKeyAlias = System.getenv("KEY_ALIAS")
        val releaseKeyPassword = System.getenv("KEY_PASSWORD")
        if (!releaseStore.isNullOrBlank() && file(releaseStore).exists()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            // CI tag 构建必须有 release 签名；本地无 Secrets 时退回 debug 签名以便自测。
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
