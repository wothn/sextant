package com.sextant.app.ui.util

/** Hide boilerplate from older releases while preserving Markdown in the actual notes. */
internal fun releaseNotesForDisplay(notes: String, version: String): String =
  notes.lineSequence().filterNot { line ->
    val text = line.trim()
    text == "## Sextant v$version" || text == "## Sextant $version" ||
      (text.startsWith("自 [") && text.contains(" 以来的变更：[完整对比]("))
  }.joinToString("\n").trim()
