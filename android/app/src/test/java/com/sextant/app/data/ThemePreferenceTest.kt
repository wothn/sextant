package com.sextant.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
  @Test
  fun storageValuesRoundTrip() {
    ThemePreference.entries.forEach { preference ->
      assertEquals(preference, ThemePreference.fromStorage(preference.storageValue))
    }
  }

  @Test
  fun unknownStorageValueDefaultsToSystem() {
    assertEquals(ThemePreference.System, ThemePreference.fromStorage("unknown"))
    assertEquals(ThemePreference.System, ThemePreference.fromStorage(null))
  }
}
