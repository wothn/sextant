# Sextant

Sextant 是一个基于 **Android 原生 Kotlin + Jetpack Compose** 的个人记账应用，当前聚焦本地优先的记账体验，支持：

- 支出 / 收入记录
- 账户管理
- 转账记录
- 月度预算
- 标签系统
- CSV 导出备份

UI 采用 **Material 3 / Jetpack Compose Material3**，数据层使用 Android 原生 `SQLiteOpenHelper` 本地存储。

## 功能概览

- **极速记账**：面向高频输入场景，优先保证录入速度
- **账户维度管理**：现金、银行卡、钱包等账户统一管理
- **预算提醒基础能力**：按分类维护月预算
- **本地数据导出**：支持导出交易 CSV 进行备份
- **离线可用**：当前不依赖云同步

## 技术栈

- Android Gradle Plugin 8.6
- Kotlin 2.0
- Jetpack Compose
- Material 3
- Android SQLite

## 快速开始

### 环境要求

- Android Studio / Android SDK
- JDK 17+

### 启动项目

```bash
.\gradlew.bat :android:app:assembleDebug
```

## 常用命令

```bash
.\gradlew.bat :android:app:assembleDebug
```

## 项目结构

```text
android/app/            Android 原生应用模块
  src/main/java/
    com/sextant/app/
      data/             SQLite schema、仓库、领域模型
      ui/theme/         Material 3 颜色与主题
      MainActivity.kt   Compose 页面与交互入口
```

## 开发约定

- 业务逻辑优先放在 `android/app/src/main/java/com/sextant/app/data`
- UI 使用 Compose Material3 组件
- 主题色需保持 `SextantColors` 中的既有色板

## 测试与质量门槛

当前仓库的原生 Android 验证入口是：

- 构建检查：`.\gradlew.bat :android:app:assembleDebug`

## 当前范围说明

这个项目目前以 **本地优先 MVP** 为主：

- 重点做 Android 原生开发体验
- 暂不包含云同步
- 暂不配置 CI / 发布构建流程
