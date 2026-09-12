plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.sextant.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.sextant.app"
    minSdk = 26
    targetSdk = 36
    versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 6
    versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.3.0"
  }

  val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
  val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
  val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
  val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
  val hasReleaseSigning =
    !releaseStoreFile.isNullOrBlank() &&
      !releaseStorePassword.isNullOrBlank() &&
      !releaseKeyAlias.isNullOrBlank() &&
      !releaseKeyPassword.isNullOrBlank()

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseStoreFile)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }

  buildTypes {
    getByName("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }
    getByName("release") {
      signingConfig = signingConfigs.findByName("release")
    }
  }

  buildFeatures {
    compose = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.jvmArgs(
          "--add-opens=java.base/java.lang=ALL-UNNAMED",
          "--add-opens=java.base/java.util=ALL-UNNAMED",
          "--add-opens=java.base/java.io=ALL-UNNAMED",
          "--add-opens=java.base/java.net=ALL-UNNAMED",
          "--add-opens=java.base/java.security=ALL-UNNAMED",
          "--add-opens=java.base/java.text=ALL-UNNAMED",
          "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
          "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
          "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        )
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

dependencies {
  // Compose 主体继续用 compose-bom（本项目的 compileSdk 是 36，够用），
  // 只有 material3 单独往上提。
  //
  // 为什么必须显式提 material3：M3 Expressive 的公开 API（MaterialExpressiveTheme /
  // MotionScheme / MaterialShapes / LoadingIndicator / ButtonGroup / ToggleButton /
  // ShortNavigationBar …）在 1.4.0 stable 里被标成了 internal，只有 1.5.0-alpha 才有；
  // 而 compose-bom 目前最高的 2026.09.00 仍然只 pin 到 material3 1.4.0，覆盖不到。
  //
  // 为什么停在 alpha18：从 1.5.0-alpha19 起 material3 依赖 compose 1.12+，而 compose 1.12
  // 要求 compileSdk 37（本机只装到 36.1）。alpha18 依赖 compose 1.11.0-beta02，
  // 与 BOM 给的 1.11.2 同一条线，Expressive 的公开 API 一应俱全。
  // 等 compileSdk 升到 37 时再一起往上走。
  val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation("androidx.compose.material3:material3:1.5.0-alpha18")

  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("androidx.work:work-runtime:2.11.2")

  debugImplementation("androidx.compose.ui:ui-tooling")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.16")
  testImplementation("androidx.work:work-testing:2.11.2")
}
