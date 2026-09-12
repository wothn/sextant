# Sextant 设计规范

本文件记录 Sextant 的视觉规范、设计 token 和组件约定。修改界面、主题、图标、图表或交互前先阅读本文件。

## 1. 设计定位

Sextant 是日常使用的原生 Android 个人财务应用。金额、记录和操作结果是视觉主角，界面应当安静、精确、清晰。

视觉语言采用 **Material 3 Expressive 的形体 + Sextant 自己的配色**：形状更圆、字重更重、动效带一点回弹，配色仍然是深浅对比的中性底加铜橙点缀。表达力放在**形体和动作**上，不放在颜色上——颜色只用来区分层级、选中和状态。

## 2. 品牌表达的边界

- 保留少量稳定的识别元素：铜橙强调色、六分仪几何标记、有数据意义的细线刻度。
- 六分仪标记由弧线、支线和定位点构成，作为页面或品牌的小型签名；不要给每个区块、列表项重复加标记。
- 刻度应表达真实比例、时间或数值。不要加入没有含义的编号、坐标、状态码和装饰性英文。
- 角色立绘、插画背景、游戏徽章、大面积蔷薇纹样、军事警示条和发光 HUD 不属于当前方向。
- 文案使用“明细”“预算”“自动记账”等日常词语，不将功能包装成游戏术语。
- 正式 logo 采用细线六分仪：两条支线、一段弧线和顶部实心定位点。矢量源文件为 `assets/branding/sextant-logo.svg`，PNG 为 `assets/branding/sextant-logo.png`。
- 桌面图标与应用内 `SextantMark` 使用相同几何图形。桌面版使用暖白底、铜橙线条，适当加粗并留出裁切空间；应用内 24dp 标记使用主题强调色。
- Expressive 指的是形状、排版和动效，不是装饰：不加纹理、不加渐变、不加无意义的形变。

## 3. 颜色

颜色集中维护在 `android/app/src/main/java/com/sextant/app/ui/theme/SextantTheme.kt` 的 `SextantColors` 和双主题 palette 中。页面使用语义 token，不重复硬编码以下色值。**色值本身没有随这次 Expressive 改造变化。**

| Token | 浅色 | 深色 | 用途 |
| --- | --- | --- | --- |
| Background | `#F7F5F0` | `#171815` | 整页背景 |
| Surface | `#FFFFFF` | `#20211F` | 独立表面、导航、表单分组 |
| SurfaceAlt | `#EFEDE8` | `#292B27` | 轻度分区、图标底色 |
| Text | `#262724` | `#EEECE5` | 正文、金额、主要标题 |
| TextMuted | `#5B5D57` | `#C4C5BC` | 次级说明 |
| TextSubtle | `#70726B` | `#A0A398` | 时间、来源等辅助信息 |
| Border | `#DCD8D0` | `#383B33` | 分隔线（已大幅少用） |
| Accent | `#A6532D` | `#E6A078` | 关键操作、选中状态、数据重点 |
| AccentStrong | `#874023` | `#F0B895` | 强调文字和图标 |
| AccentSoft | `#F3E7DF` | `#352A24` | 轻度选中背景 |

### 色调阶梯

Expressive 版界面靠**色调分层**划分层级，不再靠 1dp 描边。阶梯上的每个色值都取自上面的 palette，没有引入新色相。

| Token | 浅色取自 | 深色取自 | 用途 |
| --- | --- | --- | --- |
| `SurfaceContainerLowest` | Background | Background | 页面底色 |
| `SurfaceContainerLow` | Surface | Surface | 卡片、分组面板、菜单、弹窗、底栏 |
| `SurfaceContainer` | SurfaceAlt | BackgroundSoft | 面板里的凹槽：输入框、chip、图标底块、未选中行 |
| `SurfaceContainerHigh` | BackgroundSoft | SurfaceAlt | 凹槽里的凹槽、tonal 按钮底 |
| `SurfaceContainerHighest` | SurfaceStrong | SurfaceStrong | 轨道、进度槽 |
| `SelectedContainer` | AccentSoft | AccentSoft | 选中填充 |

两套主题各自单调：浅色主题阶越高越暗，深色主题阶越高越亮。**新增区域时先想清楚它属于哪一级，不要退回描边。**

### 使用规则

- 大部分面积保持中性，铜橙用于有意义的重点，避免整页橙色卡片或所有图标都套橙底。
- 强调色背景上的内容使用 `OnAccent`，不可假设两种主题都适合白字。
- `Success`、`Danger`、`Warning`、`Info` 表达状态。错误和超预算使用警示语义，不挪用品牌色代替。
- 普通交易金额可以保持正文色，用正负号和文字传达收支，不让所有支出看起来都是错误。
- 用户配置的分类颜色保留其分类含义，图表可以使用。不要为统一品牌而改写持久化的分类颜色。
- 首页明细的分类图标沿用分类选择器中的分类色，底色使用分类色的 12% 不透明度；分类名称、金额和元数据使用正文或次级文字色。
- 两种主题同时考虑，不通过简单反色生成深色界面。不默认启用会覆盖品牌配色的系统动态取色。
- 只有 `SextantOutlinedButton` 还保留 1dp 描边——描边是「描边按钮」唯一的定义。其余控件一律不描边。

## 4. 排版

字号阶梯在 `SextantTheme.kt` 的 `SextantTypography` 中定义。角色名沿用 Material 的语义名称，**尺寸和行高取自 M3 的类型比例，字重换成 M3 Expressive 的 emphasized 档位**：display/headline 用 SemiBold，title/label 用 Bold，body 保持常规字重以免整屏发糊。全表启用 `tnum` 数字特性，金额对齐。

| 场景 / 样式 | 字号 / 行高 | 字重 |
| --- | --- | --- |
| 核心金额 / headlineLarge | 40 / 48 sp | SemiBold，字距 -0.25 |
| 页面标题 / headlineMedium | 28 / 36 sp | SemiBold |
| displaySmall | 36 / 44 sp | SemiBold |
| headlineSmall | 24 / 32 sp | SemiBold |
| 区块标题 / titleLarge | 22 / 28 sp | SemiBold |
| 强调标题 / titleMedium | 16 / 24 sp | Bold，字距 0.15 |
| titleSmall | 14 / 20 sp | Bold，字距 0.1 |
| bodyLarge | 16 / 24 sp | Normal，字距 0.5 |
| bodyMedium | 14 / 20 sp | Normal，字距 0.25 |
| bodySmall | 12 / 16 sp | Normal，字距 0.4 |
| labelLarge | 14 / 20 sp | Bold，字距 0.1 |
| labelMedium | 12 / 16 sp | Medium，字距 0.5 |
| labelSmall | 11 / 16 sp | Medium，字距 0.5 |

- 需要临时加强某一处时用 `TextStyle.sextantEmphasized()`，不要在调用处手写 `fontWeight`。它的规则取自 M3：≥500 的提到 700，否则提到 500。
- 行高比旧版紧（bodyMedium 从 22 收到 20），明细列表更整齐；改行高前先看长备注会不会挤在一起。
- 长金额、大字体和窄屏下优先保持完整金额与操作可达；辅助说明可换行或适度省略。不要为了固定布局截断关键财务数据。

## 5. 形状、动效与布局

### 形状

形状阶梯在 `SextantShapes` / `SextantRadius` 中定义。取值全部落在 M3 Expressive 的形状 token 上，但整体比基线**高一档**：这不会让任何控件失控，却足以让面板、卡片和弹窗一眼看出比旧版更圆。

| Token | 值 | 用途 |
| --- | --- | --- |
| extraSmall | 8dp | 图表柱上沿、小图标底块 |
| small | 12dp | 紧凑图标底块、chip 前导块 |
| medium | 16dp | 卡片、分组面板、可选行 |
| large | 20dp | 选中态、设置图标块 |
| extraLarge | 28dp | 弹窗、底部面板顶部 |
| `SextantFullShape` | 胶囊 | 按钮、chip、导航指示条、轨道 |
| `SextantFieldShape` | 上两角 4dp | 填充式输入框 |
| `SextantBarShape` | 上两角 8dp | 图表柱 |
| `SextantRadius.Pressed` | 12dp | 按钮按下时的形变目标 |

- 按钮、chip、分段控件和导航指示条一律胶囊。形状本身表达「这是操作」。
- 做形状形变时用 `morphCorner(active, activeRadius, inactiveRadius)`，它按 Expressive 空间弹簧在两个 `Dp` 圆角之间插值。百分比圆角无法插值，胶囊用 `SextantRadius.Full`（32dp）代替。
- 形状只在表达状态变化时形变：按钮按下收紧再弹回、选中行圆角生长、导航指示条变宽。不做循环形变。

### 动效

动效 token 在 `SextantMotion` 中，直接读主题里的 `MotionScheme`（`SextantTheme` 装的是 `MotionScheme.expressive()`）。

| 规格 | 用途 |
| --- | --- |
| `fastSpatial` | 按钮按压、指示条收缩等要立刻反馈的小动作 |
| `defaultSpatial` | 位移、尺寸、圆角生长、进度填充 |
| `slowSpatial` | 大面积展开 |
| `fastEffects` | 短促的淡入淡出 |
| `defaultEffects` | 颜色、透明度切换 |
| `slowEffects` | 大面积的色彩过渡 |

- **位移、尺寸、旋转、圆角用 `*Spatial`**（expressive 的空间弹簧会轻微过冲，这是手感的来源）；**颜色和透明度用 `*Effects`**（临界阻尼，不允许过冲，否则会出现不存在的中间色）。
- 这些函数是 `@Composable` 的：弹簧来自 CompositionLocal。在非 composable 的 lambda（例如 `AnimatedContent` 的 `transitionSpec`）里用，要先把值取出来再传进去——`SextantTabContent` 就是这么写的。
- 反馈要短、要落在手指上。不做循环装饰动画；唯一循环的是「真的没有进度可报」的加载指示器和波浪进度条。
- 尊重系统的减弱动效设置；减弱动效时位移类动画应退化为淡入淡出。

### 布局与操作

- 主页面以 20dp 水平边距为基准，标题区由 `SextantFlexibleHeader` 承担，不再在列表里重复页面标题。
- 同层级区块保持相同的间距节奏。新增间距优先从 4、8、12、16、20、24dp 中选择。
- 明细使用整页列表，通过日期、留白和分类图标分组，**不再画行间细线**；卡片用于独立信息或表单分组，避免卡片套卡片。
- 可点击区域至少以 48dp 为目标；视觉克制不能以缩小触摸区域为代价。低矮按钮（32 / 40dp）用 `minimumInteractiveComponentSize` 补足点击范围。
- 核心操作容易找到，编辑、筛选等次级操作保持轻量。保留点击、选中、禁用和错误反馈。
- 尊重系统返回、键盘、安全区域和字体缩放。

## 6. 页面与组件约定

### 页面骨架

主页面（概览 / 分析 / 预算 / 设置）统一用「固定标题 + 滚动内容」：

```
Column {
  SextantFlexibleHeader(title, subtitle, scrollState = listState, actions)
  LazyColumn(state = listState) { ... }
}
```

标题在列表之外，随滚动从 headlineMedium（带六分仪标记和副标题）收成 titleMedium 单行，不跟着滚出屏幕。改造前标题是列表的第一项，滚下去就不知道自己在哪一页。列表的 `contentPadding` 只留底部余量，左右留白由每一项自己带。

### 概览与明细

首页与各选择器共用 `CategoryIcons.kt` 中的 `categoryIcon` 与 `CategoryIcon`：按具体分类名映射图标，未知名称使用通用分类图标；图标容器为 36dp、`shapes.small` 圆角、12% 分类色底，图标为 20dp。筛选、记账和分类管理共用 `CategoryBrowser.kt` 的列表与两级浏览。首页主标题显示具体分类名称，父分类放在次级说明中。

分类选择只有 `CategoryBrowser` 一套实现。不要再引入并行的网格或列表选择器。

首页保留核心金额、区间汇总、可选预算进度、按日分组明细的结构。当前核心金额为今日支出，不在视觉改版中擅自改变统计口径。

### 预算与分析

`BudgetScale` 是共享预算刻度组件，四分刻度对应 25%、50%、75%，填充限制在 0–100%。刻度以「切穿轨道的缺口」表现，缺口色默认取页面底色（`notchColor`），所以它要放在页面底色上。超预算的真实数值和状态仍由周边文字表达，不能因图形截满而隐藏超额信息。

图表标明单位、时间和数值。颜色服务于分类或重点，空数据不能画成真实零值趋势。视觉工作不改变预算算法、统计时间范围和收入支出定义。

### 导航与记账

底栏是一行：左边四个目的地，右边记账 FAB。

- 导航用 `SextantNavigationBar` + `SextantNavigationItem`，选中态是 AccentSoft 药丸指示条加铜橙图标与标签，指示条宽度在 40dp 与 64dp 之间形变。导航层级与目的地不擅自改变。
- 记账是 `SextantFab`，**单击直接打开记账面板**，不展开菜单。
  - 为什么没有「记支出 / 记收入」菜单：支出和收入不是两个动作，是同一个动作的一个参数。记账面板顶部本来就有收支方向的三段选择（还多一个转账），在 FAB 上先选一次只会让高频的记支出每次多一次点击，而且比面板少一个入口，同一维度上出现两套不一致的入口。
  - FAB 菜单适合放**面板里没有的**并列动作（扫一扫、从剪贴板粘贴）。等真有这类动作时再加，不要为了用这个形态而用。
- **FAB 不浮在内容之上。** 它曾经是悬浮的，正好压住明细最右列——也就是金额。现在它和导航栏同处底栏，永远不覆盖任何一行数据。
- 不使用 material3 的 `ShortNavigationBar`：它被放进外层 Layout 后只按一个条目布局（见第 8 节）。

### 设置、记账与筛选

设置按功能分组，使用 tonal 面板和足够的行内边距，**不再画行间分隔线**。记账、筛选和预算编辑继承同一套配色、排版与形状。表单包含输入、选中、取消、保存、错误等完整状态。

### 通用控件

菜单、确认弹窗、按钮、图标按钮、输入框、卡片、分组面板、可选行、筛选 chip、开关、单选按钮、分隔线、底部面板、导航栏、FAB、加载与进度指示统一使用 `SextantControls.kt` 的 `Sextant*` 组件。页面负责状态、文案、校验和事件；默认外观在共享组件中维护。

- 菜单：`SextantSurface` 底、medium 圆角、无描边、3dp 阴影。
- 确认弹窗：Surface 底、extraLarge 圆角、无色调叠加，长内容可滚动。
- 按钮：全圆角胶囊，四档尺寸 `SextantButtonSize`（32 / 40 / 56 / 96dp，取自 M3 按钮 token）。按下时圆角收成 12dp 再弹回。
  - `SextantButton`：Accent 底，用于主操作。
  - `SextantTonalButton`：`SurfaceContainerHigh` 底加正文色，用于次级操作。**次按钮不再用「描边 + 铜橙文字」**，两个按钮并排时不会都在喊。
  - `SextantOutlinedButton`：唯一保留描边的控件，用于取消 / 重置这类需要和填充按钮拉开距离的操作。
  - `SextantTextButton`：无底色，只有内容色。
- 图标按钮：`SextantIconButton`、`SextantFilledIconButton`、`SextantIconToggleButton`（选中态 AccentSoft 填充 + 形状形变）。Material 3 已保证 48dp 点击区域。
- 分段按钮组：`SextantSegmentedButtonGroup`，内部是官方的 `ButtonGroup`。同一维度、互斥、两三个选项的切换用它（记账的收支方向、筛选取值范围），不要再手写「圆角底 + 选中变色」——选中项用 `connectedButtonCheckedShape`（胶囊）并略微展开，这是 M3 定义的形态。
- 输入框：`SextantTextField`，**填充式、无描边**。底为 `SurfaceContainer`，只留一条随状态变色的底线：未聚焦透明、聚焦 2dp 铜橙、出错 Danger。顶部 4dp 圆角是 M3 填充输入框的规定形状。筛选面板的日期格（`DateFieldTile`）与输入框共用同一套凹槽外观。
- 卡片与面板：`SextantCard` / `SextantSurface`（medium 圆角、`SurfaceContainerLow` 底、零阴影、无描边）。面板内部的凹槽用 `SextantInset` 或 `SextantColors.SurfaceContainer`。空状态用 `EmptyCard`（凹槽色底）。
- 可选行与可选格：`SextantSelectableSurface`。选中态只有一种写法：AccentSoft 填充加 AccentStrong 内容，圆角从 medium 长到 large，并提供选中语义。**不再画选中描边。**
- 筛选 chip：`SextantFilterChip`，胶囊形状，未选中 `SurfaceContainer` 底、选中 AccentSoft 底，都不描边；选中且调用方没有提供前导图标时自动补一个勾（只靠颜色区分选中对色觉障碍不友好）。已选条件标签（`ActiveFilterChip`）沿用同一胶囊形状与配色。
- 分隔线：`SextantDivider`。淡化边框之后只在真正需要「同一块里的并列项」时使用，默认 Border 色。
- 单选按钮：`SextantRadioButton`，选中 Accent、未选中 TextMuted。
- 分类 chip：`FlowChips` 的 `icon` 参数表示「挑一个分类」，前导元素使用 20dp 的 `CategoryIcon` 底块；不传 `icon` 表示「挑一个颜色」，才使用 `ColorDot`。分类在任何位置都必须是淡色底图标块，不能退化成色点。
- 底部面板：28dp 顶部圆角、Surface 底、统一拖动柄；日期与时间弹窗分别使用 `SextantDatePickerDialog` 和 `SextantTimePickerDialog`，弹窗外壳和内容必须共用 `sextantDatePickerColors()` / `sextantTimePickerColors()`。
- 加载与进度：
  - `SextantProgressTrack`：圆头进度槽，数值变化用空间弹簧生长。
  - `SextantWavyProgressIndicator`：官方的 `LinearWavyProgressIndicator`，用于「正在导出 / 恢复备份」这类没有真实百分比的等待。
  - `SextantLoadingIndicator`：官方的 `ContainedLoadingIndicator`，在一组 `MaterialShapes` 多边形之间做形状形变（`SextantMotion` 的弹簧由组件内部决定）。`size` 默认 48dp，可缩到 20–24dp 行内使用。只在真的没有进度可报时用。
- 文案标题：无滚动的页面用 `SectionTitle`（headlineMedium 加六分仪标记），页面内区块和面板标题用 `SextantHeading`（titleLarge）。主页面标题用 `SextantFlexibleHeader`。
- `SextantBadge`：计数徽标，用于筛选入口这类「有条件的数量」提示。

Material 3 的默认外观不是 Sextant 的外观：菜单默认取 `surfaceContainer`、分隔线默认取 `outlineVariant`，与 `SextantColors` 并不相等，深色主题下会明显偏离。因此业务页面和组件不得直接使用未经包装的 Material 3 控件外观；缺少合适组件时，先在 `SextantControls.kt` 补一个 `Sextant*` 包装。

## 7. 代码入口

以下路径均相对于 `android/app/src/main/java/com/sextant/app/`：

| 文件 | 责任 |
| --- | --- |
| `ui/theme/SextantTheme.kt` | 配色、色调阶梯、形状、排版、`SextantMotion` 动效 token、双主题及系统栏 |
| `ui/components/LedgerIdentity.kt` | `SextantMark`、`BudgetScale` |
| `ui/components/SextantControls.kt` | 菜单、弹窗、按钮、分段按钮组、图标按钮、输入框、表面、可选行、chip、开关、导航栏、FAB、加载与进度、页面标题、底部面板与日期时间 |
| `ui/components/CommonComponents.kt` | `SectionTitle`、`SextantHeading`、空状态、徽标、色点、`FlowChips` |
| `ui/components/SextantTransitions.kt` | 列表入场 `SextantRevealItem`、主页面切换 `SextantTabContent` |
| `ui/navigation/LedgerNavigationBar.kt` | 底部导航的目的地接线 |
| `ui/components/QuickEntryDialog.kt` | 记账与编辑面板 |
| `ui/components/LedgerFilterSheet.kt` | 筛选面板 |
| `ui/screens/` | 各页面布局 |
| `ui/util/DisplayFormatters.kt` | 金额与日期等展示格式 |

## 8. UI 验证与维护

- 复用语义 token 和共享组件；共享规则变化时同步更新本文件。
- 样式由 `UiStyleTokensTest` 守卫：它扫 `ui/` 源码，禁止在 `theme/SextantTheme.kt`、`SextantControls.kt`、`CommonComponents.kt` 之外出现 `RoundedCornerShape(` 与硬编码颜色，也禁止页面直接使用 Material 3 外观组件（清单已扩到导航、App Bar、FAB、SegmentedButton、Slider、Badge、ListItem 等）。新增共享控件时把文件加进该测试的允许清单，不要放宽到整个 `ui` 目录。
- 修改共享组件时检查相关页面和表单。
- 检查浅色、深色、窄屏、长金额、大字体、空状态和键盘展开后的可用性。
- 有可见变化时使用模拟器或设备验证，并提供实际截图或录屏；截图不包含真实敏感账务。
- 测试与构建遵循 `AGENTS.md`。实际检查结果及未验证范围写入当次交付说明。

### material3 版本与 Expressive API 的边界

Sextant 的 `android/app/build.gradle.kts` **不用 compose-bom 管 material3**，而是单独指定：

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| `androidx.compose:compose-bom` | 2026.05.01 | 管 compose-ui / foundation / animation / icons，解析到 1.11.2 |
| `androidx.compose.material3:material3` | **1.5.0-alpha18** | 单独提上去，理由见下 |

为什么必须单独提：M3 Expressive 的公开 API 在 **material3 1.4.0 stable 里全部是 `internal`**，应用代码引用不到；只有 1.5.0-alpha 才公开。而 compose-bom 目前最高的 2026.09.00 仍然只 pin 到 material3 1.4.0，覆盖不到。

为什么停在 **alpha18**：从 1.5.0-alpha19 起 material3 依赖 compose 1.12+，而 compose 1.12 要求 `compileSdk 37`（本机只装到 36.1）。alpha18 依赖 compose 1.11.0-beta02，与 BOM 给的 1.11.2 同一条线。**升 compileSdk 到 37 时可以把 material3 一起推到最新的 alpha28 + compose 1.13。**

现在直接用官方 API 的部分：

- `MaterialExpressiveTheme` + `MotionScheme.expressive()` → `SextantTheme`；
- `MaterialTheme.motionScheme` 的六个弹簧规格 → `SextantMotion`；
- `Shapes` 的 `largeIncreased`（20dp）等官方形状 token，以及 `ShapeDefaults` → `SextantShapes`；
- `Typography` 的 15 个 emphasized 变体 → `SextantTypography`；
- `ButtonDefaults` 的尺寸阶梯（`ExtraSmallContainerHeight` / `MediumContainerHeight` / `LargeContainerHeight`、各档 `ContentPadding` / `IconSize` / `IconSpacing`）与 `shapesFor()` 给出的 `shape` / `pressedShape` → `SextantButtonSize`、`SextantButton`；
- `ButtonGroup` + `ButtonGroupScope.toggleableItem` + `ButtonGroupDefaults.OverflowIndicator` → `SextantSegmentedButtonGroup`；
- `ContainedLoadingIndicator`（内部用 `MaterialShapes` 做多边形形变）→ `SextantLoadingIndicator`；
- `LinearWavyProgressIndicator` → `SextantWavyProgressIndicator`。

仍然自绘的部分，以及理由：

- **底部导航栏**（`SextantNavigationBar` / `SextantNavigationItem`）。官方的 `ShortNavigationBar` 只有在作为 `bottomBar` 的直接内容时才按条目数布局；一旦被包进外层 Layout（哪怕给足整屏宽度），它只渲染一个居中条目——4 个目的地只剩 1 个可见。在 1.4.0 stable 和 1.5.0-alpha18 上都能复现。
- **记账 FAB 的展开菜单**（已取消）。FAB 现在直接打开记账面板。官方的 `FloatingActionButtonMenu` 会把菜单项浮在 FAB 上方盖住内容，而它盖的正是金额；更重要的是「记支出 / 记收入」本来就不是两个并列动作，没有理由为它多收一次点击。
- **圆角形变** `morphCorner`。`MaterialShapes` 是有机形状库，用在选中态或面板上纯属装饰。真正需要有机形状形变的地方是加载指示器，已经交给官方组件；剩下的只是两个圆角之间的数值插值。

已知 API 变动带来的注意点：

- `TimePickerDefaults.colors` 与 `ShortNavigationBarItemDefaults.colors` 的参数名在 1.5.0 的 alpha 之间改过（`clockDialContentColor` ↔ `clockDialUnselectedContentColor`、`selectedTextColor` ↔ `selectedTextColorTopIconPosition`）。升级 material3 版本时这两个地方要一起看。
- 由于 material3 是 alpha，升级前先跑 `.\gradlew.bat :android:app:testDebugUnitTest :android:app:assembleDebug`，再看一眼记账面板的分段按钮组和导航栏。
