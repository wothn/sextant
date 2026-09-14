plugins {
  id("com.android.application") version "9.2.1" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
  id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
  // KSP 的版本号自带 Kotlin 版本前缀，必须和上面 kotlin 插件的 2.3.21 配成同一条线。
  id("com.google.devtools.ksp") version "2.3.12" apply false
}
