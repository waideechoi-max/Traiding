plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 서명키는 저장소에 넣지 않고 CI가 GitHub Secrets에서 복원한 경로를 환경변수로 넘깁니다.
val signingKeystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val signingPassword: String? = System.getenv("SIGNING_STORE_PASSWORD")

android {
    namespace = "com.trendfollow.journal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trendfollow.journal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (signingKeystorePath != null && signingPassword != null) {
            create("release") {
                storeFile = file(signingKeystorePath)
                storePassword = signingPassword
                keyAlias = "trendjournal"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    testImplementation("junit:junit:4.13.2")
}
