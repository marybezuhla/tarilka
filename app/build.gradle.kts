import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// The version people see (major.minor.patch), set by hand before each release in version.txt.
val appVersion = rootProject.file("version.txt").readText().trim()
// versionCode comes from the same file, so anyone can rebuild the same APK from source:
// 1.2.0 -> 10200 (always above the CI run numbers used up to 1.1.1).
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (a, b, c) ->
    require(b < 100 && c < 100) { "version.txt: minor and patch must be below 100" }
    a * 10000 + b * 100 + c
}

android {
    namespace = "ua.tarilka"
    compileSdk = 36

    defaultConfig {
        applicationId = "ua.tarilka"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    // No Google-encrypted dependency list in the APK (F-Droid and IzzyOnDroid reject it).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // "full" is the GitHub build with the optional on-device AI (LiteRT-LM, prebuilt native libraries);
    // "fdroid" is the same app without the AI runtime, built from source by F-Droid.
    flavorDimensions += "dist"
    productFlavors {
        create("full") { dimension = "dist" }
        create("fdroid") { dimension = "dist" }
    }

    buildTypes {
        release {
            // R8: smaller, faster, and no dead code or log calls in what people install
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Built unsigned: CI signs it in a separate step with the Android SDK's apksigner.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Room writes the database structure here; scripts/check_migrations.py compares the migrations with it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    val room = "2.7.2"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Optional app protection: encrypted diary (SQLCipher), opened with fingerprint or the phone PIN
    implementation("net.zetetic:sqlcipher-android:4.19.1@aar")
    implementation("androidx.sqlite:sqlite:2.5.2")
    implementation("androidx.biometric:biometric:1.1.0")

    // Optional sync with Health Connect (steps, sleep and cycle in; meals, drinks, weight out)
    implementation("androidx.health.connect:connect-client:1.1.0-beta02")

    // On-device Gemma 4 runtime (Google AI Edge, Apache 2.0)
    "fullImplementation"("com.google.ai.edge.litertlm:litertlm-android:0.18.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (Android provides it on the phone)
    testImplementation("org.json:json:20240303")
}

// Both flavors write the same Room schema file (app/schemas): never at the same time, or one reads it half-written.
tasks.configureEach {
    if (name.startsWith("kspFdroid") && name.endsWith("Kotlin")) mustRunAfter(name.replace("kspFdroid", "kspFull"))
}
