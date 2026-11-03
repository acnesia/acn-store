plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.acn.store"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.acn.store"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Public catalog (HTTPS, GitHub) and reviews API (ACN backend, routes/store.js)
        // The API endpoint is not behind the raw CDN cache (5 min), the raw URL is the fallback
        buildConfigField("String", "CATALOG_API_URL", "\"https://api.github.com/repos/acnesia/acn-store/contents/catalog.json\"")
        buildConfigField("String", "CATALOG_URL", "\"https://raw.githubusercontent.com/acnesia/acn-store/main/catalog.json\"")
        buildConfigField("String", "REVIEWS_API", "\"http://141.11.120.62:20021/api/store\"")
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
