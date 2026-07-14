plugins {
    id("com.android.application")
}

android {
    namespace = "com.forgptstas.neurorecorder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.forgptstas.neurorecorder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
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

    androidResources {
        noCompress += listOf(
            "fst", "mdl", "raw", "vec", "mat", "conf", "int", "carpa", "ark"
        )
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    implementation("com.alphacephei:vosk-android:0.3.75")
}
