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
    versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 3
    versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.1.2"
  }

  val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
  val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
  val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
  val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
  val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
  ).all { !it.isNullOrBlank() }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseStoreFile!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    getByName("release") {
      signingConfig = signingConfigs.findByName("release")
    }
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
