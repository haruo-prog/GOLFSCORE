plugins {
    id("com.android.application")
}

android {
    namespace = "jp.co.nkts.scoremanager"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.co.nkts.scoremanager"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "1.21.0"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("free") {
            dimension = "edition"
            applicationIdSuffix = ".free"
            versionNameSuffix = "-free"
            resValue("string", "app_name", "Golf Scorecard Free")
            buildConfigField("boolean", "PAID_EDITION", "false")
        }
        create("paid") {
            dimension = "edition"
            applicationIdSuffix = ".paid"
            versionNameSuffix = "-paid"
            resValue("string", "app_name", "Golf Scorecard Pro")
            buildConfigField("boolean", "PAID_EDITION", "true")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
