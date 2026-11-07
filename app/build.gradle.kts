plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.xilonalpha.xilonai"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.xilonalpha.xilonai"
  minSdk = 29
  targetSdk = 35
  versionCode = 1
  versionName = "0.2.0"
  ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
 }
 externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
}
