plugins {
    id("com.android.application")
}

android {
    namespace = "com.forgptstas.neurorecorder"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.forgptstas.neurorecorder"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-O3", "-std=c++17")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.work:work-runtime:2.10.0")
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx-android:1.13.4")
    implementation("com.google.mediapipe:tasks-genai:0.10.27")
    testImplementation("junit:junit:4.13.2")
}
