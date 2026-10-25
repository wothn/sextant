# Sextant

一款 Android 记账应用，用来记录日常收支、了解消费去向，也可以和 AI 一起分析账本。

基于 Kotlin、Jetpack Compose 和 Material 3 构建。

## 能做什么

- **日常记账**：记录收入、支出和支付来源，按分类整理消费。
- **自动捕获**：识别支付通知，结合商户规则自动分类。
- **预算与计划**：管理月度预算，查看房租、订阅等周期支出。
- **对话助手**：用文字或账单图片记账，查询和分析消费；修改账本前由你确认。
- **桌面小组件**：在桌面查看收支、预算和近期记录，快速添加一笔。
- **备份与恢复**：导出与恢复完整账本。

## AI 助手

对话助手使用你自己的 DeepSeek API Key。相关对话、图片和查询结果会发送到 DeepSeek，API 费用由你的账户承担。

## 本地开发

需要 JDK 21 和 Android SDK 36，可使用 Android Studio 打开项目。

```powershell
# 构建 Debug APK
.\gradlew.bat :android:app:assembleDebug

# 单元测试
.\gradlew.bat :android:app:testDebugUnitTest
```

macOS / Linux 使用 `./gradlew`。应用模块为 `android/app`，主要代码位于 `android/app/src/main/java/com/sextant/app/`：

- `data/`：Room 持久化、仓库和账本计算。
- `automation/`：通知解析与自动捕获。
- `agent/`：模型接入、工具执行和会话管理。
- `ui/`：Compose 页面与组件。

提交到 `main` 或创建 PR 时，CI 会运行单元测试和 Debug 构建。
