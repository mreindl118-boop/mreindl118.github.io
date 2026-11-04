import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI sets VERSION_NAME (1.0.<run>). versionCode follows it, so every release installs over the last one.
val versionNameEnv: String = (System.getenv("VERSION_NAME") ?: "1.0.0").removePrefix("v")
val versionCodeEnv: Int = versionNameEnv.split(".").map { it.toIntOrNull() ?: 0 }
    .let { (it.getOrElse(0) { 0 } * 1_000_000) + (it.getOrElse(1) { 0 } * 10_000) + it.getOrElse(2) { 0 } }
    .coerceAtLeast(1)

// CI writes keystore.properties from the forever key. Without it the build is debug-signed and never shipped.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.mattlabs.planlab"
    compileSdk = 36

    defaultConfig {
        // Permanent. A new ID would be a different app and lose Health Connect's history window.
        applicationId = "com.mattlabs.planlab"
        minSdk = 28
        targetSdk = 36
        versionCode = versionCodeEnv
        versionName = versionNameEnv
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The food table is shared with the web page: planlab/data/foods.json ships inside the app (and refreshes from the site).
    sourceSets["main"].assets.srcDir("../../planlab/data")
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-process:2.9.4")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
