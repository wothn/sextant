# Sextant

Sextant 是一个基于 **Android 原生 Kotlin + Jetpack Compose** 的个人记账应用，当前聚焦本地优先的记账体验，支持：

- 支出 / 收入记录
- 消费来源记录
- 月度预算
- 周期支出（房租、宽带、会员这类固定节奏的支出）
- 分类体系
- 版本化完整备份与恢复

UI 采用 **Material 3 / Jetpack Compose Material3**，数据层使用 **Room**（实体 + DAO + 迁移，schema 导出在 `android/app/schemas/`）做本地持久化。

## 功能概览

- **极速记账**：面向高频输入场景，优先保证录入速度
- **来源记录**：记录现金、银行卡、微信、支付宝等消费或收入来源，为后续自动记账保留衔接点
- **商户自动分类**：已识别商户的消费支出优先使用用户记住的分类，再匹配常见餐饮、打车品牌；编辑流水时可选择「以后该商户也使用此分类」，在自动记账设置中修改或删除规则。商户信息和用户规则随完整备份恢复，规则变更不重写历史流水
- **预算提醒基础能力**：按分类维护月预算
- **周期支出**：手动记录房租、宽带、会员等按固定节奏发生的支出，按周期折算成每月固定支出，并给出未来 30 天的扣费刻度。周期支出只是计划，不会生成流水，也不会改变账本的收支口径
- **本地数据备份**：支持通过系统文件选择器导出和恢复完整 JSON 账本
- **桌面小组件**：今日支出、快捷记账、本月预算、月度收支、近期扣费和最近消费共六款；最近消费展示最多三笔，点击可查看或编辑。每个组件可独立选择外观和隐藏金额，点击更新时间可刷新
- **离线可用**：当前不依赖云同步
- **对话记账与分析**：使用 Koog 和用户自己的 DeepSeek API Key，自主查询账本，生成新增、修改、删除草稿，确认后才修改流水；已执行操作可准备撤销

## 对话助手

在「对话」页右上角配置 DeepSeek API Key 和模型名称，默认 `deepseek-flash`。对话、图片和工具查询结果会发送到 DeepSeek，费用由用户的 API 账户承担；本地账本功能继续支持离线使用。Key 通过 Android Keystore 加密存于本机，对话、图片及 Key 不进入账本备份。

查询工具支持自由 SQLite SELECT / WITH、关联与聚合。查询在只包含交易、分类、支付来源、预算和周期支出的独立只读快照上运行，不开放生产连接、诊断数据或内部设置。单次查询最多 5 秒、100 行和约 24000 字符，截断会明确标记；快照时间通过工具返回。

对话中会展示 DeepSeek 实际返回的思考内容和流式回答，以及按顺序排列的工具调用。思考可展开，工具卡片可查看参数、结果、耗时和错误。执行记录随对话保存在本机，重启后仍可查看；模型未返回思考内容时会明确说明。流式界面更新经过节流，避免每个 token 都写磁盘或刷新系统通知。

用户消息以靠右气泡显示，助手回复不使用气泡背景，并支持 Markdown 标题、加粗、列表、引用和代码块；流式回答也会同步渲染。

右上角的「新对话」图标会开始空白聊天，原会话自动保留。点击「对话历史」图标可按最近更新时间查找和继续已有会话，消息、执行过程和变更草稿随会话保存；任务运行中需先停止才能切换。升级前保存的当前对话也会保留。

输入框左侧可从系统相册选择图片，每条消息最多 4 张，原图最大 20MB；支持预览、移除、点击看大图，以及不附带文字直接发送。图片缩放压缩、移除元数据后保存到本机私有目录，历史会话中仍可查看。模型上下文最多携带最近 8 张图片，较早图片省略时会明确告知模型。图片识别使用 `deepseek-flash`；`deepseek-v4-pro` 不支持图片。图片中的记账信息仍只生成待确认草稿。

写入工具只能准备草稿。确认时检查金额、分类、来源和原记录版本，并将操作回执与账本变更同事务保存，重复提交不会重复记账。继续发消息会使先前未确认草稿失效。撤销同样生成确认卡片；记录已被其他入口修改时会拒绝覆盖。

用户发起任务后，前台服务和运行通知支持切换页面、切后台与锁屏继续分析，也可主动停止。单次任务最多 15 分钟，并有 Koog 迭代次数上限。系统终止进程后，服务重投递可重新分析；未自动恢复的任务可点击「重新分析」，不会自动执行草稿。系统强行停止应用后，需要重新打开。当前保留最近 40 条消息作为模型上下文，不提供跨对话长期记忆。

## 技术栈

- Android Gradle Plugin 9.2.1
- Kotlin Compose / Serialization 插件 2.3.21
- Jetpack Compose
- Material 3
- Room（KSP 生成 DAO 实现，schema 版本与迁移都在 `data/SextantDatabase.kt`）

## 快速开始

### 环境要求

- Android Studio / Android SDK
- JDK 21

### 启动项目

```bash
.\gradlew.bat :android:app:assembleDebug
.\gradlew.bat :android:app:testDebugUnitTest
.\gradlew.bat :android:app:testDebugUnitTest :android:app:assembleDebug
```

## 常用命令

```bash
.\gradlew.bat :android:app:assembleDebug
```

## CI 与版本发布

GitHub Actions 只在推送 `v` 开头的版本标签时构建正式版本，例如：

```bash
git tag v1.1.0
git push origin v1.1.0
```

标签触发后，Actions 会构建签名 release APK、生成 SHA-256 校验文件，并创建 GitHub Release。Release 正文由上一个版本标签到本次标签之间的提交生成，按提交类型（`feat`、`fix`、`ui`……）分节，所以提交信息请保持 `type(scope): summary` 的形式。首次发布前，需要在仓库的 `Settings > Secrets and variables > Actions` 中配置：

- `ANDROID_KEYSTORE_BASE64`：release keystore 文件的 Base64 内容
- `ANDROID_STORE_PASSWORD`：keystore 密码
- `ANDROID_KEY_ALIAS`：签名 key alias
- `ANDROID_KEY_PASSWORD`：签名 key 密码

Windows PowerShell 可用以下命令生成 keystore 的 Base64 内容：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sextant-release.jks"))
```

keystore 和密码只能保存在安全位置或 GitHub Secrets 中，不要提交到仓库。

## 项目结构

```text
android/app/            Android 原生应用模块
  src/main/java/
    com/sextant/app/
      data/             Room 实体、DAO、迁移、仓库和领域计算
      agent/            Koog 工具、DeepSeek 接入、对话协调和后台执行服务
      ui/theme/         Material 3 颜色与主题
      ui/components/    共享控件、导航和业务编辑器
      ui/LedgerViewModel.kt  页面数据投影与加载状态
      MainActivity.kt   Activity、ViewModel 与主题接线
```

## 开发约定

- 业务逻辑优先放在 `android/app/src/main/java/com/sextant/app/data`
- UI 使用 Compose Material3 组件
- 主题色需保持 `SextantColors` 中的既有色板

## 测试与质量门槛

当前仓库的原生 Android 验证入口是：

- 构建检查：`.\gradlew.bat :android:app:assembleDebug`
- 本地 JVM 测试：`.\gradlew.bat :android:app:testDebugUnitTest`
- CI：推送到 `main` 或创建 Pull Request 时自动执行单元测试和 debug 构建

测试覆盖数据库建库与旧版本迁移、完整备份恢复及事务回滚、流水核心计算和金额输入边界。

## 当前范围说明

这个项目目前以 **本地优先 MVP** 为主：

- 重点做 Android 原生开发体验
- 暂不包含云同步
- 暂不引入账户体系，优先聚焦消费追踪与快速记账
- 通过版本标签触发 GitHub Actions 签名发布
