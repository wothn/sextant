package com.sextant.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
  @Test fun legacyReleaseBoilerplateIsHiddenAndMarkdownIsPreserved() {
    val notes = """
      ## Sextant v0.5.4

      自 [v0.5.3](https://github.com/wothn/sextant/releases/tag/v0.5.3) 以来的变更：[完整对比](https://github.com/wothn/sextant/compare/v0.5.3...v0.5.4)。

      ### 🐛 问题修复

      - **ui**: slide between main pages
      - **chat**: restore reading position after tab changes
    """.trimIndent().replace("\n", "\r\n")

    assertEquals("""
      ### 🐛 问题修复

      - **ui**: slide between main pages
      - **chat**: restore reading position after tab changes
    """.trimIndent(), releaseNotesForDisplay(notes, "0.5.4"))
  }

  @Test fun ordinaryNotesAndLinksArePreserved() {
    val notes = "### 问题修复\n\n- 自上次更新以来修复了显示问题。\n- [使用说明](https://example.com/help)"
    assertEquals(notes, releaseNotesForDisplay(notes, "0.5.4"))
    assertEquals("首个正式版本。", releaseNotesForDisplay("首个正式版本。", "0.5.4"))
  }

  @Test fun missingNotesAndBoilerplateOnlyNotesAreBlank() {
    assertEquals("", releaseNotesForDisplay(" \r\n ", "0.5.4"))
    assertEquals("", releaseNotesForDisplay("## Sextant 0.5.4\n\n", "0.5.4"))
  }
}
