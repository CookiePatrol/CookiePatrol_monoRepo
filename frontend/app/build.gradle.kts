plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val cookiePatrolApiBaseUrl = providers.gradleProperty("cookiePatrolApiBaseUrl")
    .orElse("http://10.0.2.2:8000")
    .get()

android {
    namespace = "com.cookiepatrol"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cookiepatrol"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"$cookiePatrolApiBaseUrl\"")
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
    testImplementation("junit:junit:4.13.2")
}
