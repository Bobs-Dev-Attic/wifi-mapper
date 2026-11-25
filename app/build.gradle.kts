plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bobsdevattic.wifimapper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bobsdevattic.wifimapper"
        minSdk = 26          // Android 8.0. ARCore needs 24+; 26 gives us modern APIs.
        targetSdk = 35
        // Version is overridable from CI (release workflow passes the git tag):
        //   ./gradlew assembleRelease -PappVersionName=0.2.0 -PappVersionCode=200
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 3
        versionName = (project.findProperty("appVersionName") as String?) ?: "0.3.0"

        // GitHub repo the in-app updater checks for new releases.
        buildConfigField("String", "UPDATE_REPO", "\"Bobs-Dev-Attic/wifi-mapper\"")
    }

    // Release signing. CI exports these env vars from GitHub secrets; when absent
    // (local builds, PR CI) we fall back to the debug key so assembleRelease still
    // yields an installable APK. For working in-app auto-update, publish ALL
    // releases from CI with the same keystore so signatures stay consistent.
    val releaseKeystore: String? =
        System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() && file(it).exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (releaseKeystore != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true // exposes BuildConfig.VERSION_NAME / VERSION_CODE in-app
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Export Room schemas so DB versions are tracked in git (schemas/ is committed).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Positioning / AR
    implementation(libs.play.services.location)
    implementation(libs.arcore)
    implementation(libs.sceneview.ar)

    // Permissions + coroutines
    implementation(libs.accompanist.permissions)
    implementation(libs.kotlinx.coroutines.android)
}
