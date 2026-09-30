import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.navigation.safe.args)
    alias(libs.plugins.protobuf)
    kotlin("kapt")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

/** Sets one build type's AdMob IDs: BuildConfig fields for code + manifest placeholder for the app ID. */
fun com.android.build.api.dsl.ApplicationBuildType.admob(
    appId: String,
    bannerAdUnitId: String,
    rewardedAdUnitId: String
) {
    manifestPlaceholders["admobAppId"] = appId
    buildConfigField("String", "ADMOB_APP_ID", "\"$appId\"")
    buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"$bannerAdUnitId\"")
    buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"$rewardedAdUnitId\"")
}

android {
    namespace = "com.pahntd.expensetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pahntd.expensetracker"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    // BASE_URL: single source of truth for the backend base URL, per build type. Must end with "/".
    // Cleartext for the debug LAN host is allowed only by src/debug/res/xml/network_security_config.xml.
    //
    // AdMob: single source of truth for the app ID and ad unit IDs, per build type. Code reads them
    // via AdsConfig; the app ID also reaches AndroidManifest.xml through the admobAppId placeholder.
    // debug = Google's public TEST IDs, release = the real Expense Tracker IDs.
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://192.168.43.103:8080/\"")
            admob(
                appId = "ca-app-pub-3940256099942544~3347511713",
                bannerAdUnitId = "ca-app-pub-3940256099942544/6300978111",
                rewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917"
            )
        }
        release {
            buildConfigField("String", "BASE_URL", "\"https://expense-tracker-backend-lx3d.onrender.com/\"")
            admob(
                appId = "ca-app-pub-5720492551902022~5577716530",
                bannerAdUnitId = "ca-app-pub-5720492551902022/4018232017",
                rewardedAdUnitId = "ca-app-pub-5720492551902022/7003488007"
            )
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
        buildConfig = true
    }
}

// Proto DataStore: generate Java "lite" protobuf classes from src/main/proto.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.runtime)
    // Fragment
    implementation(libs.androidx.fragment)
    // RecyclerView
    implementation(libs.androidx.recyclerview)
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    // Hilt
    implementation(libs.google.hilt.android)
    kapt(libs.google.hilt.compiler)
    implementation(libs.androidx.hilt.navigation)
    // WorkManager (+ Hilt integration)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)
    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    // DataStore (Proto)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    // Session encryption (Tink AEAD + Android Keystore)
    implementation(libs.tink.android)
    // Charts (Statistics)
    implementation(libs.mpandroidchart)
    // Ads (Next-Gen Google Mobile Ads SDK + User Messaging Platform)
    implementation(libs.ads.mobile.sdk)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}