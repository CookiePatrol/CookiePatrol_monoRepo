plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cookiePatrolApiBaseUrl = providers.gradleProperty("cookiePatrolApiBaseUrl")
    .orElse("http://10.0.2.2:8000")
    .get()
val cookiePatrolDebugApiBaseUrl = providers.gradleProperty("cookiePatrolDebugApiBaseUrl")

android {
    namespace = "com.cookiepatrol"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cookiepatrol"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
        buildConfigField("String", "API_BASE_URL", "\"$cookiePatrolApiBaseUrl\"")
    }
    buildTypes {
        debug {
            cookiePatrolDebugApiBaseUrl.orNull?.let { url ->
                buildConfigField("String", "API_BASE_URL", "\"$url\"")
            }
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
