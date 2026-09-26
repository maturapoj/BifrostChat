import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

// API key lives in local.properties (gitignored), never in source.
// Release CI passes the git tag (e.g. v1.2.3); local builds are "0.0.0-dev".
val releaseVersion: String = System.getenv("TOKENFLOW_VERSION_NAME")?.removePrefix("v") ?: "0.0.0-dev"

/** 1.2.3 → 10203, so a higher semver always installs over a lower one. Allows minor/patch up to 99. */
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = Regex("""(\d+)\.(\d+)\.(\d+)""").find(version)?.destructured
        ?: return 1
    return major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.maturapoj.tokenflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.maturapoj.tokenflow"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeOf(releaseVersion).coerceAtLeast(1)
        versionName = releaseVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from the environment (CI secrets); without it, release builds are unsigned.
    val keystorePath: String? = System.getenv("TOKENFLOW_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("TOKENFLOW_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TOKENFLOW_KEY_ALIAS")
                keyPassword = System.getenv("TOKENFLOW_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Debug builds may preload an endpoint from local.properties so development needs no typing.
        // Release builds never embed one: users enter it in Settings, so no key ships in the APK.
        debug {
            buildConfigField("String", "DEFAULT_BASE_URL", "\"${localProps.getProperty("tokenflow.baseUrl", "")}\"")
            buildConfigField("String", "DEFAULT_API_KEY", "\"${localProps.getProperty("tokenflow.apiKey", "")}\"")
        }
        release {
            buildConfigField("String", "DEFAULT_BASE_URL", "\"\"")
            buildConfigField("String", "DEFAULT_API_KEY", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // kotlin.time.Clock / Instant are still experimental in Kotlin 2.2.
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

room {
    // Checked in so schema changes show up in review and migrations can be tested.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    implementation("io.insert-koin:koin-android:4.1.0")
    implementation("io.insert-koin:koin-androidx-compose:4.1.0")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("io.insert-koin:koin-test-junit4:4.1.0")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.8.4")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
