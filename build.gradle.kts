plugins {
  id("com.android.application") version "9.2.1" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
  id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
  // KSP 独立锁定版本；升级 Kotlin、AGP 时一并验证 Room 代码生成。
  id("com.google.devtools.ksp") version "2.3.12" apply false
}
