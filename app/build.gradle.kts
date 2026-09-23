plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.poultryscanfinal"

    // The .tflite model must stay uncompressed in the APK, otherwise it cannot
    // be memory-mapped by the TFLite Interpreter and loading fails at runtime.
    androidResources {
        noCompress += "tflite"
    }

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.poultryscanfinal"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
    implementation(libs.constraintlayout)
    implementation("androidx.core:core:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room (local user database)
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // TensorFlow Lite.
    // The supplied model reports min_runtime_version = 2.17.0 and uses
    // FULLY_CONNECTED v12 (per-channel int8 weights). Runtime 2.14/2.16 cannot
    // parse that operator version, so the interpreter must be 2.17.0 or newer.
    implementation("org.tensorflow:tensorflow-lite:2.17.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
