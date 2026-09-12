package com.sextant.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 样式冻结守卫。
 *
 * 外观只允许来自主题 token 和共享控件：页面不得写死圆角或颜色，也不得直接使用 Material 3 的
 * 外观组件。这些规则靠人眼复查会漏——未选中 chip 曾经取到 M3 默认的 `surface` 与
 * `outlineVariant`，白底比铜橙选中态还抢眼，就是扫出来的。所以改成扫源码。
 *
 * 新增共享控件时把文件加进 [tokenFiles] 或 [lookFiles]；不要图省事把整个 ui 目录放宽。
 */
class UiStyleTokensTest {

  private val uiRoot: File by lazy {
    val relativeRoots = listOf(
      "src/main/java/com/sextant/app/ui",
      "android/app/src/main/java/com/sextant/app/ui",
    )
    generateSequence(File("").absoluteFile) { it.parentFile }
      .flatMap { base -> relativeRoots.asSequence().map { File(base, it) } }
      .firstOrNull { File(it, "theme/SextantTheme.kt").isFile }
      ?: throw IllegalStateException("找不到 ui 源码目录，测试工作目录是 ${File("").absolutePath}")
  }

  /** 定义颜色、圆角 token 的文件。 */
  private val tokenFiles = setOf("SextantTheme.kt", "SextantControls.kt", "CommonComponents.kt")

  /**
   * 唯一允许接触 Material 3 组件外观的文件。
   *
   * 导航栏曾经也在里面（直接用 `ShortNavigationBar`），现在收进了
   * `SextantControls.kt` 的 `SextantNavigationBar`，所以这里只剩一个文件：
   * M3 的默认外观只在一个地方被改写，其余文件都必须走 `Sextant*` 包装。
   */
  private val lookFiles = setOf("SextantControls.kt")

  private val materialLook = Regex(
    "\\b(" + listOf(
      "FilterChip", "AssistChip", "InputChip", "SuggestionChip",
      "OutlinedTextField", "TextField", "Card", "ElevatedCard", "OutlinedCard",
      "ModalBottomSheet", "AlertDialog", "DatePickerDialog", "TimePickerDialog",
      "DatePicker", "TimePicker", "DropdownMenu", "DropdownMenuItem",
      "HorizontalDivider", "VerticalDivider",
      "Button", "OutlinedButton", "TextButton", "ElevatedButton", "FilledTonalButton",
      "IconButton", "FilledIconButton", "OutlinedIconButton",
      "Checkbox", "RadioButton", "Switch", "Slider", "RangeSlider",
      "LinearProgressIndicator", "CircularProgressIndicator",
      "Surface", "TopAppBar", "CenterAlignedTopAppBar", "MediumTopAppBar", "LargeTopAppBar",
      "BottomAppBar", "AppBarRow", "AppBarColumn",
      "NavigationBar", "ShortNavigationBar", "ShortNavigationBarItem",
      "NavigationRail", "WideNavigationRail", "NavigationDrawer",
      "SegmentedButton", "SingleChoiceSegmentedButtonRow", "MultiChoiceSegmentedButtonRow",
      "ListItem", "Badge", "Snackbar",
      "FloatingActionButton", "SmallFloatingActionButton", "LargeFloatingActionButton",
      "ExtendedFloatingActionButton",
    ).joinToString("|") + ")\\(",
  )

  private val hardcodedColor = Regex(
    "Color\\(0x[0-9A-Fa-f]{6,8}\\)" +
      "|Color\\.(White|Black|Red|Green|Blue|Gray|LightGray|DarkGray|Yellow|Cyan|Magenta)\\b",
  )

  private val uiSources: List<File> by lazy {
    uiRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
  }

  @Test
  fun `圆角只来自主题或共享控件`() {
    assertClean(Regex("RoundedCornerShape\\("), tokenFiles)
  }

  @Test
  fun `颜色只来自主题`() {
    assertClean(hardcodedColor, setOf("SextantTheme.kt"))
  }

  @Test
  fun `页面不直接使用 Material 外观组件`() {
    assertClean(materialLook, lookFiles)
  }

  /** 守卫本身也要能认违规：只扫源码不断言表达式，写法写错时会静默失效。 */
  @Test
  fun `守卫能识别违规写法并放行共享控件`() {
    listOf(
      materialLook to listOf(
        "Card(colors = CardDefaults.cardColors())" to true,
        "FilterChip(selected = true, onClick = {})" to true,
        "OutlinedTextField(value = \"\", onValueChange = {})" to true,
        "ShortNavigationBarItem(selected = true, onClick = {})" to true,
        "FloatingActionButton(onClick = {})" to true,
        "Modifier.background(Color.White)" to false,
        "SextantFilterChip(selected = true, onClick = {})" to false,
        "SextantCard { Text(\"x\") }" to false,
        "SextantSelectableSurface(selected = true) {}" to false,
        "SextantTextButton(onClick = {}) { Text(\"x\") }" to false,
        "SextantDatePicker(state = pickerState)" to false,
        "SextantFab(onClick = {}, icon = icon, contentDescription = \"记账\")" to false,
      ),
      hardcodedColor to listOf(
        "Color(0xFFA6532D)" to true,
        "Color.White" to true,
        "Color.Transparent" to false,
        "SextantColors.Accent" to false,
      ),
      Regex("RoundedCornerShape\\(") to listOf(
        "RoundedCornerShape(12.dp)" to true,
        "MaterialTheme.shapes.large" to false,
      ),
    ).forEach { (pattern, samples) ->
      samples.forEach { (sample, shouldMatch) ->
        assertTrue(
          "「$sample」应当${if (shouldMatch) "" else "不"}匹配 $pattern",
          pattern.containsMatchIn(sample) == shouldMatch,
        )
      }
    }
  }

  private fun assertClean(pattern: Regex, allowedFiles: Set<String>) {
    val violations = uiSources
      .filter { it.name !in allowedFiles }
      .flatMap { file ->
        pattern.findAll(withoutComments(file.readText())).map { "${file.name}: ${it.value}" }
      }
    assertTrue(
      "位置绕过共享样式（换成主题 token 或 Sextant* 组件）：\n${violations.joinToString("\n")}",
      violations.isEmpty(),
    )
  }

  /** 去掉注释，避免 KDoc 里提到组件名就误报。 */
  private fun withoutComments(source: String): String = source
    .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
    .replace(Regex("//[^\n]*"), " ")
}
