plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.sextant.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.sextant.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0.0"
  }

  buildFeatures {
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")
}
