plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// O app usa a pasta ../site (a mesma do GitHub Pages) como cópia offline.
val copySite by tasks.registering(Copy::class) {
    from(rootProject.file("../site")) {
        exclude("*.apk")
    }
    into(layout.buildDirectory.dir("generated/siteAssets/site"))
}

android {
    namespace = "com.sakuga.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sakuga.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
        buildConfigField("String", "SITE_URL", "\"https://kaiqueguimacuper-bit.github.io/teste-2/\"")
    }

    signingConfigs {
        create("sakuga") {
            storeFile = rootProject.file("sakuga-release.jks")
            storePassword = "sakuga2026"
            keyAlias = "sakuga"
            keyPassword = "sakuga2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sakuga")
        }
        debug {
            signingConfig = signingConfigs.getByName("sakuga")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/siteAssets"))

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

tasks.named("preBuild") { dependsOn(copySite) }

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
