plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.kapt")
    id("androidx.compose.ui")
    id("androidx.compose.compiler")
}

android {
    namespace = "com.example.hermesmobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hermesmobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Allow embedding Python/proot in native libs
        externalNativeBuild {
            cmake {
                cppFlags ""
            }
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.0"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    namespaceSuffix ".mobile"
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.annotation:annotation:1.7.0")

    // Compose UI
    implementation("androidx.compose.ui:ui:1.4.0")
    implementation("androidx.compose.ui:ui-unit:1.4.0")
    implementation("androidx.compose.ui:ui-graphics:1.4.0")
    implementation("androidx.compose.ui:ui-input:1.4.0")
    implementation("androidx.compose.ui:ui-text:1.4.0")
    implementation("androidx.compose.ui:ui-unit:1.4.0")
    implementation("androidx.compose.ui:ui-navigation:1.4.0")
    implementation("androidx.compose.ui:ui-material:1.4.0")
    implementation("androidx.compose.ui:material-icons-core:1.4.0")
    implementation("androidx.compose.ui:material-icons-extended:1.4.0")

    // Compose lifecycle and remember
    implementation("androidx.compose.runtime:runtime-livedata:1.4.0")
    implementation("androidx.compose.runtime:runtime-rxjava2:1.4.0")
    implementation("androidx.compose.runtime:runtime-rxjava3:1.4.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Shizuku API
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // Termux terminal library
    implementation("com.termux.termux-app:terminal-view:0.118.0")

    // OKHTTP for gateway WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Lifecycle testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}