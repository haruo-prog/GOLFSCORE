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
        versionCode = 25
        versionName = "1.13.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
