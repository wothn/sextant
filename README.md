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
- **应用更新**：启动时检查 GitHub Release，查看更新说明并下载安装新版本。

## 应用更新

正式版进入前台时自动检查 `wothn/sextant` 的最新正式 Release，成功检查后 24 小时内不重复请求；失败后至少间隔 1 小时重试。也可以在「设置 → 关于 → 检查更新」手动检查。

发现新版本后，由你确认下载，下载完成后点击「安装更新」。首次安装需要在系统设置中允许 Sextant 安装未知应用，授权返回后会继续打开安装界面。下载会校验附件大小、SHA-256、包名、版本号和签名。调试版使用独立包名，不参与正式版覆盖更新。

发布沿用现有工作流：推送 `vX.Y.Z` 标签，Release 附件保留 `sextant-X.Y.Z.apk` 和 `.apk.sha256` 文件，并使用相同的发布签名。更新只接受比当前 `versionCode` 更高的正式版本。下载可跨页面和屏幕旋转继续；若进程被系统终止，需要重新下载。

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

提交到 `main` 或创建 PR 时，CI 会运行单元测试。
