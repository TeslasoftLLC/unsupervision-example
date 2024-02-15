plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "org.teslasoft.core.unsupervision"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.teslasoft.core.unsupervision"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.teslasoft.id)
    implementation(libs.okhttp)
    implementation(libs.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.swiperefreshlayout)
    implementation(libs.security.crypto.ktx)
    implementation(libs.customactivityoncrash)
    implementation(libs.nanohttpd)
    implementation(libs.androidx.core.splashscreen)
}
