<p align="center">
  <img src="assets/branding/sextant-logo.png" width="96" alt="Sextant logo">
</p>

<h1 align="center">Sextant</h1>

<p align="center">记录日常收支，和 AI 一起看懂账本。</p>

<p align="center">
  <a href="https://github.com/wothn/sextant/releases/latest">下载 APK</a> ·
  <a href="https://github.com/wothn/sextant/issues">反馈问题</a> ·
  <a href="LICENSE">MIT</a>
</p>

Sextant 是一款原生 Android 记账应用，使用 Kotlin、Jetpack Compose 和 Material 3 构建。从记下一笔消费，到查看月度开销、安排预算，都可以在这里完成。

## 功能

- **日常账本**：记录收支与转账，按分类、支付方式和日期整理，查看日历与消费统计。
- **自动记账**：识别支付通知和支付页面，结合商户规则减少手动录入。
- **AI 助手**：用文字或账单图片记账，也可以直接提问、分析消费；账本变更由你确认后执行。
- **预算与计划**：设置分类预算，管理房租、订阅等周期支出。
- **桌面小组件**：查看近期账目、预算和待办支出，快速添加记录。
- **备份与恢复**：导出账本，保留自己的数据副本。

## 下载

在 [Releases](https://github.com/wothn/sextant/releases/latest) 下载最新 APK，支持 Android 8.0 及以上版本。安装后可在应用内检查更新。

## 数据与 AI

账本保存在本机。AI 助手需要自行配置 DeepSeek API Key，使用时相关对话、图片和查询结果会发送到 DeepSeek，API 费用由你的账户承担。

## 开发

需要 JDK 21 和 Android SDK 36，可用 Android Studio 打开仓库。

```powershell
.\gradlew.bat :android:app:assembleDebug
.\gradlew.bat :android:app:testDebugUnitTest
```

macOS / Linux 使用 `./gradlew`。应用模块为 `:android:app`，源码位于 `android/app/src/main/java/com/sextant/app/`，按数据、自动捕获、AI 助手和界面分层组织。

欢迎通过 [Issues](https://github.com/wothn/sextant/issues) 反馈问题、提出建议，或提交 Pull Request。发布流程见 [发布文档](.github/RELEASING.md)。

## 许可证

[MIT](LICENSE)。第三方依赖遵循各自的许可证。
