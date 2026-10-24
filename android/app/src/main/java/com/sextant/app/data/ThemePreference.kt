package com.sextant.app.data

enum class ThemePreference(val storageValue: String) {
  System("system"),
  Light("light"),
  Dark("dark"),

  /** 跟随系统壁纸取色（Material You，Android 12+）。 */
  Dynamic("dynamic"),
  ;

  companion object {
    fun fromStorage(value: String?): ThemePreference =
      entries.firstOrNull { it.storageValue == value } ?: System
  }
}
