plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.mylocalai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mylocalai"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.0"
        
        // Endpoint comes from the gitignored .env file (or an environment variable).
        // Never hardcode a Tailscale address in published source.
        val envFile = rootProject.file(".env")
        val envValues = if (envFile.exists()) envFile.readLines().associate { line ->
            val parts = line.split("=", limit = 2)
            parts[0].trim() to parts.getOrNull(1)?.trim().orEmpty()
        } else emptyMap()
        val remoteUrl = System.getenv("REMOTE_API_BASE_URL")
            ?: envValues["REMOTE_API_BASE_URL"]
            ?: "http://localhost:8081/"
        buildConfigField("String", "REMOTE_API_BASE_URL", "\"$remoteUrl\"")
        val bridgeToken = System.getenv("BRIDGE_TOKEN") ?: envValues["BRIDGE_TOKEN"] ?: ""
        buildConfigField("String", "BRIDGE_TOKEN", "\"$bridgeToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isMinifyEnabled = false
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

    testOptions {
        unitTests.all {
            // This machine has a small paging file; avoid spawning one JVM per CPU core.
            it.maxParallelForks = 1
            it.jvmArgs("-Xmx512m", "-Dfile.encoding=UTF-8")
        }
    }
}

dependencies {
    // Core Android & Material
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.glance:glance:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    
    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Local session and message history
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Testing
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
