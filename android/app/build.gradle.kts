plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dex.adfree"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dex.adfree"
        minSdk = 26
        targetSdk = 35
        // CI sets GITHUB_RUN_NUMBER, so every build is newer than the last and installs over it.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.0"
    }

    // One fixed key for every build, so a new APK updates the installed app in place
    // (no uninstall, no second copy). Personal-use key; it only protects updates of this app.
    signingConfigs {
        create("release") {
            storeFile = file("adfree-release.jks")
            storePassword = "adfree-personal"
            keyAlias = "adfree"
            keyPassword = "adfree-personal"
        }
    }

    buildTypes {
        release {
            // Smaller and faster than debug.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.webkit:webkit:1.12.1")
}

// The app reuses the extension's own scripts and rules, so pulling upstream changes
// into the fork updates the app too. They are copied into assets/web/ before every build.
val syncWebAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../src/content")) {
        include("yt-main.js", "youtube.js", "generic.css")
    }
    from(rootProject.file("../src/rules")) {
        include("ads.json")
    }
    into(layout.projectDirectory.dir("src/main/assets/web"))
}

tasks.named("preBuild") {
    dependsOn(syncWebAssets)
}
