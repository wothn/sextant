plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("com.google.devtools.ksp")
}

/**
 * versionCode 由版本号确定性派生：`major * 10000 + minor * 100 + patch`。
 * 于是 0.4.0 = 400、1.0.0 = 10000，单调、可读，而且能从版本号直接算出来。
 *
 * 为什么不再用 `github.run_number`：那个计数是按 workflow 独立累计的，和发布次序只是碰巧
 * 相关——重跑不变，一旦重命名 workflow 或再加一个也在 `v*` 上触发的 workflow 就会脱钩。
 * 真正的麻烦在于本地构建拿不到它，只能退回一个写死的常量，于是本地包和发布包的版本号活在
 * 两个互不相干的坐标系里。一旦本地值更高，之后的发布包就会因为「版本号倒退」被系统拒绝，
 * 而系统只会报一句「应用未安装」，不告诉你原因。
 *
 * 改这个公式前先确认新算出的一串值**全部大于已经发布过的最大值**（Android 只允许
 * versionCode 不低于已安装版本，相同可以、更低会被拒）。CI 里有一条断言守着这条线。
 */
fun versionCodeFrom(name: String): Int {
  // 容错：去掉 -debug / -rc1 之类后缀。
  val parts = name.substringBefore("-").split(".")
  require(parts.size >= 3) { "VERSION_NAME 必须是 x.y.z 形式，当前为 $name" }
  val numbers = parts.take(3).map { it.toIntOrNull() ?: error("VERSION_NAME 含非数字段：$name") }
  val (major, minor, patch) = numbers
  // 超过两位就和上一级撞号（0.4.100 会算成 0.5.0 的值），宁可构建失败也不要悄悄重号。
  require(minor < 100 && patch < 100) { "VERSION_NAME 的次版本号和修订号都必须小于 100：$name" }
  return major * 10_000 + minor * 100 + patch
}

/**
 * 本地构建兜底的版本名，CI 用 tag 覆盖它。
 *
 * 保持为「最后一个已发布版本」：派生出的 versionCode 就总是比下一个发布版本小 1，本地包装
 * 在设备上不会挡住随后的正式更新。
 */
val localVersionName = "0.4.3"
val resolvedVersionName = providers.environmentVariable("VERSION_NAME").orNull ?: localVersionName

// VERSION_CODE 只留作逃生口（例如修复性重发同一版本号时需要手动抬高）。CI 不再传它。
val resolvedVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull()
  ?: versionCodeFrom(resolvedVersionName)

android {
  namespace = "com.sextant.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.sextant.app"
    minSdk = 26
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName
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

  /**
   * Room 的 schema 导出。
   *
   * 导出的 JSON 是「当前版本的建表语句」的唯一权威记录：迁移测试和以后排查
   * 「用户库里的表结构和实体对不对得上」都要靠它。关掉导出会让 Room 在编译期报错，
   * 所以这里显式打开，并把文件提交进版本库。
   */
  ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
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
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("androidx.work:work-runtime:2.11.2")

  // Room 取代了原来的 SQLiteOpenHelper：实体/DAO/迁移都由编译期校验，
  // 表结构写错会在构建时就报出来，而不是等到用户打开某个页面才崩。
  implementation("androidx.room:room-runtime:2.7.0")
  implementation("androidx.room:room-ktx:2.7.0")
  ksp("androidx.room:room-compiler:2.7.0")

  debugImplementation("androidx.compose.ui:ui-tooling")

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.16")
  testImplementation("androidx.work:work-testing:2.11.2")
}
