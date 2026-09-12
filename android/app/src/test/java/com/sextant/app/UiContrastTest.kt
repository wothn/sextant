package com.sextant.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.sextant.app.ui.theme.SextantColors
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class UiContrastTest {
  @After
  fun restoreTheme() {
    SextantColors.updateTheme(false)
  }

  @Test
  fun `small supporting text is readable on every content surface in both themes`() {
    listOf(false, true).forEach { dark ->
      SextantColors.updateTheme(dark)
      listOf(
        SextantColors.Background,
        SextantColors.SurfaceContainerLow,
        SextantColors.SurfaceContainer,
        SextantColors.SurfaceContainerHigh,
      ).forEach { background ->
        assertContrast(SextantColors.TextSubtle, background, dark)
      }
      assertContrast(SextantColors.Warning, SextantColors.Background, dark)
      assertContrast(SextantColors.OnAccent, SextantColors.Accent, dark)
    }
  }

  private fun assertContrast(foreground: Color, background: Color, dark: Boolean) {
    val light = maxOf(foreground.luminance(), background.luminance())
    val darkValue = minOf(foreground.luminance(), background.luminance())
    val contrast = (light + 0.05f) / (darkValue + 0.05f)
    assertTrue("dark=$dark, $foreground on $background: $contrast", contrast >= 4.5f)
  }
}
