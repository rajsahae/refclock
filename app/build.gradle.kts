plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.refclock"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.rajsahae.refclock"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    // The Play upload key. Its location and passwords come from ~/.gradle/gradle.properties, so they
    // never enter this repo. Without them (say, on someone else's machine) release builds fall back
    // to the debug key, which Play rejects but a watch will install.
    val uploadStoreFile = providers.gradleProperty("refclock.upload.storeFile").orNull
    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = providers.gradleProperty("refclock.upload.storePassword").get()
                keyAlias = providers.gradleProperty("refclock.upload.keyAlias").get()
                keyPassword = providers.gradleProperty("refclock.upload.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.androidx.wear)
    // androidx.wear pulls in Fragment 1.2.4, which mishandles the permission request in MainActivity.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.wear.ongoing)
    implementation(libs.androidx.wear.input)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
