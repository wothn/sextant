# Sextant 应用内更新方案（设计与实施计划）

> 状态：设计稿，尚未实现。
> 目标读者：后续实现该功能的自己 / 其他 agent。
> 相关文件：`.github/workflows/android-release.yml`、`android/app/build.gradle.kts`、`android/app/src/main/AndroidManifest.xml`、`android/app/src/main/java/com/sextant/app/ui/screens/SettingsScreen.kt`

---

## 0. 决策摘要

| 决策点 | 结论 |
|---|---|
| 更新机制 | 自建 GitHub Releases 更新器（检查 + 下载 + 调起系统安装器） |
| 是否用 Play In-App Updates | **不能**，应用不在 Play 上分发，该 API 只对 Play 安装的应用生效 |
| 是否静默安装 | **不能**，非 root / 非 DeviceOwner 应用无权免确认安装，最后一步必须用户点「安装」 |
| 是否引入第三方更新库 | 不引入，见 §2 |
| 版本发现渠道 | 首选静态清单 `latest.json`（见 §3.3），回退 GitHub Release 重定向（§3.2） |
| HTTP 客户端 | `HttpURLConnection`，不新增网络依赖 |
| 触发策略 | **待定**，推荐「启动静默检查 + 24h 节流 + 设置页手动检查」（§5） |
| 阻塞项 | ~~必须先修 `versionCode` 生成方式~~ **已修复**（§3.5），CI 已加版本倒挂断言 |

---

## 1. 现状盘点（已核对仓库）

| 事实 | 位置 | 对自更新的意义 |
|---|---|---|
| `applicationId = com.sextant.app`，debug 变体加 `.debug` 后缀 | `android/app/build.gradle.kts:12,42` | debug 与 release 可共存，更新器在 debug 下应禁用 |
| release 用固定 keystore 签名（4 个 Secret） | `android-release.yml:49-79` | 同签名升级可保留数据与通知使用权，这是自更新的前提 |
| Release 资产固定命名 `sextant-{version}.apk` + `sextant-{version}.apk.sha256` | `android-release.yml:85-89` | 资产 URL 可从版本号推导，且**已有现成的完整性校验值可直接复用** |
| `versionName` 来自 tag（去掉 `v`），本地默认 `0.3.0` | `build.gradle.kts:16` | 版本比较的正确依据 |
| `versionCode` 来自 `github.run_number`，本地默认 `6` | `build.gradle.kts:15` | ⚠️ **有隐患，见 §3.5** |
| 已有 `INTERNET` 权限 | `AndroidManifest.xml:2` | 无需新增网络权限 |
| `network_security_config` 禁止明文流量 | `res/xml/network_security_config.xml:3` | 无需修改，GitHub 全链路 HTTPS |
| 已有 `kotlinx-serialization-json:1.9.0` | `build.gradle.kts:89` | 解析更新清单无需新依赖 |
| 已有 `androidx.work:work-runtime:2.11.2` | `build.gradle.kts:91` | 定期检查可直接用 WorkManager |
| 无 `BuildConfig` 生成（AGP 8+ 默认关闭） | `build.gradle.kts:50-52` | 读版本号走 `PackageManager`，不必打开 `buildConfig` |
| 账本数据在 `sextant.db`，由 Room 管理，schema 版本 11 | `data/SextantDatabase.kt` | 升级不涉及数据迁移，但迁移路径必须继续正确（v10 → v11 是空迁移，见第 10 章教程） |

---

## 2. 方案选型

| 方案 | 说明 | 取舍 |
|---|---|---|
| **自建更新器（选定）** | 检查 + 下载 + `PackageInstaller` 安装，约 250–350 行 Kotlin | 与现有 CI 资产天然咬合，可复用 `.sha256`，无新依赖，完全可控 |
| [AppUpdater](https://github.com/javiersantos/AppUpdater) | 老牌库，原生支持 GitHub source | 已多年未维护（最后 release 2020 年），为这点功能引入不可维护依赖不划算 |
| KMP 类新库（如 app-updater） | 较新的跨平台更新库 | 面向多平台，功能过剩；本项目纯 Android 单模块 |
| [Obtainium](https://github.com/ImranR98/Obtainium) 等外部工具 | 零代码，由外部 App 轮询 GitHub Release | 等于再装一个 App，不是「应用内」更新；可作为临时兜底手段 |
| Google Play In-App Updates | Play 官方 API | **不可用**：只对 Play 安装的应用生效（`InstallSourceInfo` 不匹配时直接抛错） |

参考：[In-app Update with GitHub 讨论](https://community.kodular.io/t/in-app-update-with-github/306054)、[KMP in-app updater 介绍](https://pavi2410.com/blog/introducing-kmp-app-updater/#android)

---

## 3. 核心设计

### 3.1 硬约束（必须写进产品预期）

1. **无法静默安装。** `PackageInstaller.SessionParams.setRequireUserAction(USER_ACTION_NOT_REQUIRED)` 只对持有 `INSTALL_PACKAGES` 权限的特权/系统应用生效。普通应用提交安装后，系统一定会弹出安装确认界面。
2. **首次需要用户手动开启「安装未知应用」。** 这是一次性设置，之后每次更新只需点一次确认。
3. **Play Protect 可能提示风险。** 非 Play 来源的 APK 安装时，部分设备会显示「发送至 Google 进行安全检查」或安全警告，无法绕过。
4. **同签名 + versionCode 递增是硬前提。** 任一不满足，系统会直接拒绝安装并提示「应用未安装」。

因此功能目标应表述为：**把「开浏览器 → 找 Release → 下载 → 文件管理器里点开 → 确认安装」压缩为「App 内点一次更新 + 系统界面点一次安装」**，而不是「无感自动更新」。

### 3.2 版本发现 · 渠道 A：GitHub Release 重定向（无 API 限流）

不调用 API，直接利用 HTTP 重定向拿到最新 tag：

```
HEAD https://github.com/wothn/sextant/releases/latest
→ 302, Location: https://github.com/wothn/sextant/releases/tag/v0.3.4
→ 解析出版本号 0.3.4
```

要点：

- `HttpURLConnection` 上设 `instanceFollowRedirects = false`，读 `getHeaderField("Location")`。
- 版本号从 Location 尾部 `/tag/v{x.y.z}` 提取；解析失败即视为「检查失败」，不要猜测。
- APK 与校验值地址由命名约定推导（必须与 `android-release.yml:87-89` 保持一致）：
  - `https://github.com/wothn/sextant/releases/download/v{ver}/sextant-{ver}.apk`
  - `https://github.com/wothn/sextant/releases/download/v{ver}/sextant-{ver}.apk.sha256`
- 优点：零 API 限流，请求体积极小。
- 缺点：拿不到 release notes；`releases/latest` 在「预发布」状态下行为不同（`gh release create` 未加 `--prerelease`，当前不受影响）。

### 3.3 版本发现 · 渠道 B：静态清单 `latest.json`（推荐）

在 `android-release.yml` 创建 Release 之后，额外生成并发布一份清单，客户端只请求一个固定的静态 URL。

```json
{
  "version": "0.3.4",
  "versionCode": 12,
  "tag": "v0.3.4",
  "apkUrl": "https://github.com/wothn/sextant/releases/download/v0.3.4/sextant-0.3.4.apk",
  "sha256": "3f2c…",
  "minSupportedVersionCode": 1,
  "notes": "本次更新的简短说明",
  "publishedAt": "2026-05-01T10:00:00Z"
}
```

托管方式二选一：

- 提交到 `main` 分支的 `updates/latest.json`，客户端读 `https://raw.githubusercontent.com/wothn/sextant/main/updates/latest.json`。
- 或 GitHub Pages。

优点：

- 一个请求拿到全部信息（含 `release notes`、`versionCode`），解析逻辑最简。
- 不受 GitHub API 限流影响，且**顺带解决了 §3.5 的 versionCode 权威来源问题**。
- 可以在客户端强制校验「清单里的 `versionCode` 必须大于当前安装值」，不满足则拒绝更新，避免无用下载。

缺点：多一个 CI 步骤；`raw.githubusercontent.com` 在部分网络环境下不稳定（见 §12）。

建议：**渠道 B 为主、渠道 A 为回退**，两个都失败则降级为「打开 Release 页面」按钮。

### 3.4 版本发现 · 渠道 C：GitHub REST API（不推荐作为主路径）

`GET https://api.github.com/repos/wothn/sextant/releases/latest`

- 未认证限额 **60 次/小时/IP**；移动网络走运营商 NAT，出口 IP 与他人共享，极易被 403 限流。
- 好处是能拿到 `body`（release notes）和完整的 assets 列表（含 `browser_download_url`、`size`）。
- 结论：作为可选回退，不作为默认路径；若使用必须处理 403 并给出「稍后再试/打开网页」的降级路径。

### 3.5 `versionCode` 生成方式（已修复）

> 状态：**已按本节方案修复**——`build.gradle.kts` 用 `versionCodeFrom` 从版本号派生，
> CI 加了 `Assert versionCode does not go backwards` 断言，`github.run_number` 不再参与。
> 下面保留原始诊断，因为「为什么不能用 `github.run_number`」这个结论对以后想改这个公式的
> 人仍然成立。
>
> 实测确认（`apkanalyzer` 读 APK）：`0.4.0 → 400`、`0.4.1 → 401`、`0.3.10 → 310`、
> `1.0.0 → 10000`、`0.4.0-rc1 → 400`；`0.4.100` 与 `0.4` 会以可读原因构建失败。
> 迁移是安全的：400 大于已发布过的最大 `versionCode`（v0.4.0 为 12）。

修复前的现状是 `versionCode = providers.environmentVariable("VERSION_CODE") ?: 6`（`build.gradle.kts:15`），CI 传 `github.run_number`（`android-release.yml:74`）。

关键事实：**`github.run_number` 是「按 workflow 独立计数」的**，不是仓库全局计数。`android-release.yml` 只在打 tag 时触发，所以它的计数约等于「历史发布次数」。这意味着：

- `v0.3.0` → release workflow 第 1 次运行 → `versionCode = 1`
- `v0.3.1` → 第 2 次 → `versionCode = 2`
- `v0.3.2` → 第 3 次 → `versionCode = 3`
- `v0.3.3` → 第 4 次 → `versionCode = 4`（左右，取决于是否重跑过）

而 `build.gradle.kts` 里的**本地默认值是 `6`**。于是会出现这个局面：

> 你本地 `assembleRelease` 出的 APK，`versionCode = 6`，比 CI 发布的 `v0.3.3`（4）还高。
> 一旦装上这个本地包，后续 CI 发布的 `v0.3.4`（`versionCode = 5`）**因为版本号倒退会被系统拒绝安装**，自更新直接失效，且报错信息只有一句「应用未安装」，极难排查。

**先摸清现状**（有设备时执行）：

```powershell
adb shell dumpsys package com.sextant.app | Select-String -Pattern "versionCode|versionName"
```

**推荐的修法**：把 versionCode 改成从 semver 确定性派生，保证「版本号大 → versionCode 一定大」，且不受 workflow 重跑影响：

```kotlin
// android/app/build.gradle.kts
fun versionCodeFrom(name: String): Int {
  val parts = name.substringBefore("-").split(".") // 容错：去掉 -debug / -rc1 之类后缀
  require(parts.size >= 3) { "VERSION_NAME 必须是 x.y.z 形式，当前为 $name" }
  val (major, minor, patch) = parts.take(3).map { it.toIntOrNull() ?: error("VERSION_NAME 含非数字段：$name") }
  return major * 10_000 + minor * 100 + patch
}

val versionNameValue = providers.environmentVariable("VERSION_NAME").orNull ?: "0.3.0"
val resolvedVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull()
  ?: versionCodeFrom(versionNameValue)

android {
  defaultConfig {
    versionCode = resolvedVersionCode
    versionName = versionNameValue
  }
}
```

`0.3.4` 派生为 `304`、`1.0.0` 派生为 `10000`，单调且可读；`VERSION_CODE` 环境变量仍作为逃生口保留（例如需要修复性重发同版本号时手动抬高）。

**迁移注意**：改动后要确认新 scheme 算出的值 **大于线上所有已安装设备的当前 versionCode**。`0.3.x` 派生值是 `300 + x`（303、304…），远大于 CI 用 run_number 得到的个位数，所以是安全的。建议同时把本地默认值从 `6` 改掉，避免再出现「本地包比发布包版本号还高」的倒挂。

无论最终用哪种 scheme，都必须在 CI 里加一条断言，防止将来倒挂（见 §11）。

### 3.6 版本比较

- 权威比较依据：**`versionCode`**（整数大小比较，无歧义）。
- 显示与展示：`versionName`（semver）。
- 当前安装版本的读法（不依赖 `BuildConfig`）：

```kotlin
val info = context.packageManager.getPackageInfo(context.packageName, 0)
val currentName = info.versionName.orEmpty().substringBefore("-")  // 去掉 debug 的 "-debug"
val currentCode = PackageInfoCompat.getLongVersionCode(info)       // 需 androidx.core
```

- semver 比较函数（用于渠道 A 这种只有版本名的情况）必须是纯函数，方便单测：

```kotlin
// 返回 true 表示 candidate 比 current 新；非法输入返回 false，绝不抛异常
fun isNewerVersion(candidate: String, current: String): Boolean {
  fun parse(v: String): List<Int>? {
    val parts = v.substringBefore("-").split(".")
    return parts.map { it.toIntOrNull() ?: return null }
  }
  val a = parse(candidate) ?: return false
  val b = parse(current) ?: return false
  for (i in 0 until maxOf(a.size, b.size)) {
    val x = a.getOrElse(i) { 0 }
    val y = b.getOrElse(i) { 0 }
    if (x != y) return x > y
  }
  return false
}
```

注意：**不要用字符串比较**（`"0.10.0" < "0.9.0"` 是错的），也**不要依赖 `versionName` 带后缀的形态**。

### 3.7 debug 变体处理

debug 的 `applicationId` 是 `com.sextant.app.debug`，与 release 是两个独立应用。更新器必须：

- 在 `BuildConfig.DEBUG` 为 true 时**不注册任何检查任务**，设置页入口只显示当前版本号，不显示「检查更新」；
- 或者在 debug 下仅允许检查、禁止下载安装，并把提示文案写成「调试构建不提供应用内更新」。

否则会出现「debug 包去下载 release APK、安装时签名不匹配」的诡异失败。

---

## 4. 模块划分与状态机

新增包 `com.sextant.app.update`（放在 `data/` 之外，因为它是独立关注点；如果偏好与现有结构一致，也可放 `data/UpdateStore.kt` + `update/` 混合）：

```
update/
  UpdateModels.kt        // UpdateManifest, UpdateAvailability, UpdateState 等不可变模型
  UpdateManifestParser.kt// JSON 解析 + 字段校验（纯函数，可单测）
  VersionComparator.kt   // semver / versionCode 比较（纯函数，可单测）
  UpdateSource.kt        // 接口：suspend fun fetchLatest(): UpdateManifest?  —— 便于测试注入假实现
  GithubReleaseSource.kt // 渠道 A + B 的实现（HttpURLConnection）
  ApkDownloader.kt       // 流式下载 + 进度回调 + SHA-256 计算
  ApkVerifier.kt         // 校验值比对 + 签名证书摘要比对
  ApkInstaller.kt        // PackageInstaller 会话写入与 commit
  UpdateInstallReceiver.kt // 接收安装结果广播
  UpdateCheckerWorker.kt // WorkManager 定期检查（24h 节流）
  UpdateStore.kt         // 应用级状态：StateFlow<UpdateState>，供 UI 观察
```

UI 侧：

```
ui/components/UpdateDialog.kt   // 新版本提示 / 下载进度 / 错误态
ui/screens/SettingsScreen.kt    // 「关于」区块：当前版本 + 检查更新入口
```

状态机（`UpdateState`，由 `UpdateStore` 持有）：

```
Idle
 └─(检查)──> Checking
              ├─(无更新)──> UpToDate(checkedAt)
              ├─(有更新)──> Available(manifest)
              └─(失败)────> Failed(reason, retryable)

Available ──(下载)──> Downloading(bytesRead, total)
                        ├─(完成+校验通过)──> Ready(apkPath)
                        ├─(校验失败)──────> Failed(ChecksumMismatch)
                        └─(网络失败)──────> Failed(Network, retryable)

Ready ──(安装)──> AwaitingUserInstall  // 已调起系统安装界面
                    ├─(成功)──> Installed（进程会被系统杀掉重启）
                    ├─(取消)──> Ready（保留已下载文件，允许重试）
                    └─(失败)──> Failed(Install, message)

Failed ──(重试)──> 回到上一步
```

数据流遵循仓库既有约定：**单向**，UI 只观察 `UpdateStore` 的 `StateFlow` 并调用其方法，不直接发网络请求、不直接碰 `PackageInstaller`。

---

## 5. 触发与节流策略（待你确定）

| 策略 | 行为 | 评价 |
|---|---|---|
| **A（推荐）启动静默检查 + 24h 节流 + 设置页手动检查** | 冷启动时若距上次检查 > 24h，在 IO 后台静默请求；有新版本时只在设置页打红点/角标，不弹窗 | 不打扰记账，又不会长期错过更新 |
| B 仅设置页手动检查 | 无任何后台请求，完全按需 | 最克制，但你可能几个月想不起来点 |
| C 发现有新版本时主动弹窗 | 每次启动都可能打断 | 记账类应用被弹窗打断体验较差 |

推荐 A。实现细节：

- 节流时间戳存 `SharedPreferences`（仓库已有 `ThemePreference` 这类轻量偏好存储，可参照其写法，或直接新增 `UpdatePreferences`）。
- 冷启动检查用 `Dispatchers.IO` + `supervisorScope`，**任何失败都必须静默**（无网、代理不通、解析失败都不能影响启动，更不能弹 Toast）。
- 定期检查可选挂 WorkManager（`androidx.work` 已有），约束 `NetworkType.CONNECTED`，24h 周期，`ExistingPeriodicWorkPolicy.KEEP` 避免重复排程。
- 只在「设置页可见」时展示红点，避免在首页干扰。

---

## 6. 下载实现

```kotlin
suspend fun download(
  manifest: UpdateManifest,
  target: File,
  onProgress: (bytesRead: Long, total: Long) -> Unit,
): DownloadResult
```

要点：

- 目标路径：`File(context.cacheDir, "updates/sextant-${manifest.version}.apk")`。放 cache 目录，系统在存储紧张时清理它也不影响正确性（重新下载即可）。
- **不要用 `DownloadManager`**：会多出一条通知栏条目、进度无法回灌 UI、失败原因难拿、且控制力弱。
- 请求头建议带 `Accept: application/octet-stream` 和明确的 `User-Agent`（如 `Sextant-Android/${versionName}`），便于将来排查。
- **必须跟随重定向**：Release asset 会 302 到 `objects.githubusercontent.com` 的签名地址（`HttpURLConnection.setInstanceFollowRedirects(true)` 默认可跨协议跳 HTTPS）。
- 用 `contentLengthLong`（或清单里的申报大小）驱动进度；未知时退化为不确定进度条。
- 下载中断/失败：直接删除半成品文件并允许整包重下。**不做断点续传**——APK 只有几 MB，收益不抵复杂度与状态出错风险。
- 下载中禁用返回键退出（或明确提供「取消」，取消后删除临时文件）。
- 每次进入下载前先清理 `cacheDir/updates/` 里除目标外的旧文件，避免缓存堆积。
- 超时：连接 15s、读取 30s，避免弱网下无限挂起。

---

## 7. 校验（两层，都要做）

### 7.1 SHA-256 比对

CI 已经在生成 `sextant-{version}.apk.sha256`（`android-release.yml:89`），直接复用：

- `.sha256` 文件内容是 `{hash}  {filename}` 的 `sha256sum` 格式，解析时**取第一个空白分隔字段**，并做大小写无关比较。
- 边下载边算 digest（`MessageDigest.getInstance("SHA-256")` 流式 update），避免读完再遍历一遍文件。
- 不匹配 → 删除文件、状态置 `Failed(ChecksumMismatch)`、**绝不进入安装流程**。

### 7.2 签名证书比对

防止「下载被替换成其他签名的同包名 APK」以及「自己拿错 keystore 重新构建」：

```kotlin
fun hasSameSigner(context: Context, apkFile: File): Boolean {
  val pm = context.packageManager
  val flags = PackageManager.GET_SIGNING_CERTIFICATES   // API 28+；minSdk 26 时对 26/27 走 GET_SIGNATURES 分支
  val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return false
  val archiveSigners = archive.signingInfo?.apkContentsSigners ?: return false
  val selfInfo = pm.getPackageInfo(context.packageName, flags)
  val selfSigners = selfInfo.signingInfo?.apkContentsSigners ?: return false
  return archiveSigners.any { a -> selfSigners.any { b -> a.toByteArray().contentEquals(b.toByteArray()) } }
}
```

注意：`minSdk = 26` 而 `GET_SIGNING_CERTIFICATES` 是 API 28 引入的，26/27 上需要回退到 `GET_SIGNATURES` + `signatures`（`signingInfo` 为 null）。`@Suppress("DEPRECATION")` 局部使用即可。另外还要确认 `archive.packageName == context.packageName`。

---

## 8. 安装实现（`PackageInstaller`）

```kotlin
fun install(context: Context, apkFile: File) {
  val installer = context.packageManager.packageInstaller
  val params = PackageInstaller.SessionParams(
    PackageInstaller.SessionParams.MODE_FULL_INSTALL,
  ).apply {
    setAppPackageName(context.packageName)
    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED) // 显式声明，普通应用也只能如此
  }
  val sessionId = installer.createSession(params)

  installer.openSession(sessionId).use { session ->
    session.openWrite("base.apk", 0, apkFile.length()).use { out ->
      apkFile.inputStream().use { it.copyTo(out) }
      session.fsync(out)
    }
    val intent = Intent(context, UpdateInstallReceiver::class.java)
      .setAction(UpdateInstallReceiver.ACTION_INSTALL_RESULT)
    val pending = PendingIntent.getBroadcast(
      context,
      sessionId,
      intent,
      // ⚠️ Android 12+ 必须用 FLAG_MUTABLE，否则系统无法回填结果，commit 会失败
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    session.commit(pending.intentSender)
  }
}
```

要点与坑：

1. **`FLAG_MUTABLE` 是必需的**（Android 12+）。用 `FLAG_IMMUTABLE` 会导致安装结果永远收不到、甚至 commit 抛异常，这是最常见的踩坑点。
2. `UpdateInstallReceiver` 在 Manifest 里声明为 `android:exported="false"`；因为是本应用创建的 `PendingIntent`，系统代表本应用发送，未导出也能收到。
3. 结果通过 `Intent.EXTRA_STATUS`（`PackageInstaller.EXTRA_STATUS`）取 `PackageInstaller.STATUS_*`：
   - `STATUS_PENDING_USER_ACTION`：需要用户确认——这是**正常路径**，从 extra 取 `Intent`（`EXTRA_INTENT`）并用 `startActivity` 拉起系统安装界面。
   - `STATUS_SUCCESS`：安装成功，本进程即将被系统杀掉并重启，无需自己做重启逻辑。
   - `STATUS_FAILURE_BLOCKED`：被未知来源设置拦截 → 引导去 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`。
   - `STATUS_FAILURE_INCOMPATIBLE` / `STATUS_FAILURE_CONFLICT`：多为签名不一致或 versionCode 未递增（对应 §3.5）。
   - `STATUS_FAILURE_STORAGE`：空间不足。
4. **用 `PackageInstaller` 就不需要 `FileProvider` 和 `file_paths.xml`**（字节通过 `session.openWrite` 直接喂入）。`FileProvider` + `ACTION_VIEW` 那套老写法只在想省掉 `REQUEST_INSTALL_PACKAGES` 时才用，且同样需要该权限，没有优势。
5. **未知来源引导**（首次必做）：

```kotlin
if (!context.packageManager.canRequestPackageInstalls()) {           // API 26+
  context.startActivity(
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
  )
  return
}
```

   用户授权后回到 App 再点一次「安装」即可。判断依据是 `canRequestPackageInstalls()`（`REQUEST_INSTALL_PACKAGES` 的运行时门禁），不要靠 `checkSelfPermission`。
6. **后台启动 Activity 限制**（Android 10+）：`STATUS_PENDING_USER_ACTION` 的 `startActivity` 必须来自前台。因为安装入口在设置页，属于前台场景，没问题；但**不要在 Worker 里直接触发安装**，那会被系统静默丢弃。
7. 不要在安装成功后立刻弹出「安装成功」——进程会被杀，弹不出来。若确实想反馈，用通知（仓库已有 `AutomationNotifier` 的通知写法可参考）。

---

## 9. Manifest 与资源改动

`android/app/src/main/AndroidManifest.xml`：

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<application …>
  <receiver
    android:name=".update.UpdateInstallReceiver"
    android:exported="false">
    <intent-filter>
      <action android:name="com.sextant.app.action.INSTALL_RESULT" />
    </intent-filter>
  </receiver>
</application>
```

不需要改动：

- `network_security_config.xml`：GitHub 与 `raw.githubusercontent.com` 均为 HTTPS，现有 `cleartextTrafficPermitted="false"` 无需放宽。**不要为了图省事关掉它。**
- `INTERNET` 权限：已有。

依赖：若使用 `PackageInfoCompat.getLongVersionCode`，显式声明 `androidx.core:core-ktx`（当前仅通过 Compose 传递引入，显式声明更稳）。其余功能用现有依赖即可，**不新增网络库**。

---

## 10. UI 设计

**设置页新增「关于」区块**（`SettingsScreen.kt` 的 `SettingsOverview` 内，与既有 `SettingsSection` / `SettingsEntry` 风格保持一致）：

| 条目 | 内容 |
|---|---|
| 当前版本 | `0.3.3 (4)`（versionName + versionCode，便于你排查版本倒挂问题） |
| 检查更新 | 可点击；检查中显示转圈；有新版本时显示副标题「发现新版本 0.3.4」+ 红点 |
| （debug 构建） | 副标题「调试构建不提供应用内更新」，不可点击 |

**更新对话框**（`AlertDialog`，与 `BackupSummary` 相关弹窗风格一致）：

1. 发现新版本：标题「发现新版本 0.3.4」，正文显示 release notes（若有）+ 「更新后将保留全部账本数据」，按钮「稍后」/「下载并安装」。
2. 首次需要授权未知来源：说明「需要在系统设置中允许 Sextant 安装应用，仅需一次」，按钮「去设置」。
3. 下载中：线性进度条 + 「正在下载 0.3.4（2.1 MB / 4.6 MB）」，可取消。
4. 校验失败：标题「安装包校验失败」，正文「下载的安装包不完整或被修改，已删除。请重试。」按钮「重试」/「取消」。
5. 安装失败：复用 `SettingsScreen.kt` 里已有的 `Throwable.toDataErrorMessage()` 风格，给出中文可读原因 + 「打开 Release 页面」兜底。
6. 已是最新：轻量 Snackbar/Toast「当前已是最新版本 0.3.3」，不弹对话框。

文案全部中文，与现有 UI 语言一致；错误文案要说清「发生了什么 + 下一步做什么」，符合仓库「断言用户可见消息」的测试约定。

---

## 11. CI 改动（`.github/workflows/android-release.yml`）

1. **修 versionCode**：按 §3.5 改为 semver 派生（或 `VERSION_CODE` 显式覆盖）。同时删掉 `build.gradle.kts` 里那个危险的本地默认值 `6`。
2. **加版本倒挂断言**（防止将来再次出现 §3.5 的问题）：在构建后读取 APK 的 versionCode，与上一次 Release 的 versionCode 比较，不递增就 fail：

```yaml
- name: Assert versionCode increases
  env:
    GH_TOKEN: ${{ github.token }}
  run: |
    # 用 aapt2/apkanalyzer 或 bundletool 读新 APK 的 versionCode，
    # 与 `gh release list` 中上一个 release 的 versionCode（或 latest.json）比较，不递增则退出 1
```

3. **生成 `latest.json`**（若采用渠道 B）：在 `Create GitHub Release` 之后追加一步，写入 `updates/latest.json` 并提交到 `main`（或发布到 Pages），内容见 §3.3。
4. **资产命名契约断言**：校验 Release 里实际存在的资产名与客户端推导规则一致（`sextant-{ver}.apk`、`sextant-{ver}.apk.sha256`）。这条断言能防止「将来改了 APK 命名，更新器悄悄失效」这类最难排查的问题。
5. 需要 `permissions: contents: write`（已有）。

---

## 12. 风险与坑（重点）

| 风险 | 说明 | 对策 |
|---|---|---|
| **versionCode 倒挂** | 见 §3.5。`github.run_number` 按 workflow 独立计数，本地默认值 `6` 高于 CI 值；倒挂时系统只提示「应用未安装」，不告诉你原因 | 先按 §3.5 统一 scheme + CI 断言；设置页显示 versionCode 便于排查 |
| **签名不一致** | 换 keystore 或误用 debug 包会导致永远升不上去（`STATUS_FAILURE_INCOMPATIBLE`） | 签名比对（§7.2）；keystore 只放 Secrets，切勿丢失 |
| **国内网络访问 GitHub 不稳定** | `api.github.com` / `raw.githubusercontent.com` / Release 下载在部分网络下会超时或不可达，这是**最可能让你觉得「还不如手动下载」的地方** | ①检查失败静默 + 明确降级为「打开 Release 页面」；②超时设短（15s）；③保留可配置镜像/代理 URL 的口子（不要硬编码第三方加速域名到代码里）；④也可考虑在 Cloudflare Workers 上自建一个转发清单的小端点 |
| **API 限流 403** | 未认证 60 次/小时/IP，运营商 NAT 下易触发 | 主路径不走 API；走 API 时必须识别 403 并降级 |
| **Play Protect 提示** | 非 Play 来源安装时的安全提示 | 无法绕过，在文档/说明中提前告知，避免误以为下载被篡改 |
| **后台启动安装界面被拦** | Android 10+ 后台不能启动 Activity | 安装只在设置页前台触发，绝不在 Worker 里直接 commit |
| **`FLAG_IMMUTABLE` 导致收不到结果** | Android 12+ 必须 mutable | 见 §8 要点 1，代码注释中明确标注 |
| **缓存 APK 残留** | 长期占用存储 | 每次下载前清理，安装成功后删除 |
| **数据丢失担忧** | 同签名 + 同包名升级不会清数据 | UI 上明确写「更新后保留全部账本数据」；仍建议更新前引导用户先导出一次备份（仓库已有备份能力，成本极低） |
| **通知使用权是否保留** | 同签名升级保留已授权的权限 | 更新后可在设置页做一次状态自检，若失效则提示重新授权 |

---

## 13. 文件级落地清单

新增：

| 文件 | 职责 | 关键成员 |
|---|---|---|
| `update/UpdateModels.kt` | 不可变模型 | `UpdateManifest`、`UpdateState`（sealed）、`UpdateFailure`（sealed，携带用户可见中文消息） |
| `update/UpdateManifestParser.kt` | JSON 解析与字段校验 | `fun parse(raw: String): UpdateManifest?`（纯函数） |
| `update/VersionComparator.kt` | 版本比较 | `fun isNewerVersion(candidate: String, current: String): Boolean` |
| `update/UpdateSource.kt` | 抽象 | `interface UpdateSource { suspend fun fetchLatest(): UpdateManifest? }` |
| `update/GithubReleaseSource.kt` | 渠道 A/B 实现 | `HttpURLConnection`，处理 302 与超时 |
| `update/ApkDownloader.kt` | 流式下载 | `suspend fun download(...): DownloadResult`，带进度回调 |
| `update/ApkVerifier.kt` | 双重校验 | `verifySha256`、`hasSameSigner` |
| `update/ApkInstaller.kt` | 安装 | `fun install(context, apkFile)`、`fun needsUnknownSourcesGrant(context): Boolean` |
| `update/UpdateInstallReceiver.kt` | 安装结果 | `onReceive` 映射 `PackageInstaller.STATUS_*` |
| `update/UpdateStore.kt` | 应用级状态 | `val state: StateFlow<UpdateState>`、`checkForUpdates()`、`downloadAndInstall()` |
| `update/UpdateCheckerWorker.kt` | 定期检查（可选） | 24h 周期，`NetworkType.CONNECTED` |
| `ui/components/UpdateDialog.kt` | 更新 UI | 状态到界面的映射 |
| `updates/latest.json` | 静态清单（渠道 B） | 由 CI 生成 |

修改：

| 文件 | 改动 |
|---|---|
| `android/app/build.gradle.kts` | 修 `versionCode` 生成；必要时显式声明 `androidx.core:core-ktx` |
| `android/app/src/main/AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES` 权限 + 安装结果 receiver |
| `SextantApplication.kt` | 构造 `UpdateStore`（沿用现有 `automationCoordinator` 式的手写依赖装配） |
| `ui/screens/SettingsScreen.kt` | 「关于」区块 + 更新对话框接线 |
| `.github/workflows/android-release.yml` | 版本倒挂断言、资产命名断言、生成 `latest.json` |
| `README.md` | 补充「应用内更新」说明与「未知来源授权」步骤 |

---

## 14. 测试计划

遵循仓库既有约定：JUnit 4 + Robolectric，`@Config(sdk = [35])`，`*Test.kt`，句子式测试名，断言行为与用户可见文案。

**纯 JVM 单测（不需要 Robolectric）**

- `VersionComparatorTest`：`0.3.10 > 0.3.9`、`1.0.0 > 0.99.99`、相等返回 false、位数不齐（`0.4` vs `0.3.9`）、带 `-debug`/`-rc1` 后缀、非法输入不抛异常。
- `UpdateManifestParserTest`：正常清单、缺字段、`sha256` 长度错误、`apkUrl` 非 HTTPS、`versionCode` 非数字、空 body。
- `ReleaseAssetUrlTest`：由 `0.3.4` 正确推导 APK 与 `.sha256` 地址；`sha256` 文件内容解析（`sha256sum` 双空格格式、大小写）。
- `GithubRedirectParserTest`：从 `Location: …/releases/tag/v0.3.4` 提取版本；畸形 Location 返回 null。

**Robolectric 测试**

- `ApkVerifierTest`：SHA-256 匹配/不匹配；签名一致/不一致（可用测试 APK 或桩数据）。
- `UpdateStoreTest`：注入假 `UpdateSource`，验证状态机全路径——有无更新、网络失败静默、校验失败不进入安装、取消后保留已下载文件。
- `UpdateCheckerThrottleTest`：24h 节流生效（首次检查、节流期内跳过、过期后重查），并 pin 时区、`@After` 恢复。

**手工验证清单（必须真机跑一遍）**

1. 装上 `v0.3.3`，发布 `v0.3.4`，确认能检测到、下载、校验通过、安装成功，且**账本数据与通知使用权都在**。
2. 首次安装时验证未知来源引导流程完整走通。
3. 断网/飞行模式下启动，确认无崩溃、无报错弹窗、不影响记账。
4. 把 `latest.json` 里的 `sha256` 改错一位，确认校验失败并删除文件、不安装。
5. 用一个不同签名但同包名的 APK 替换，确认签名校验拦住。
6. 构造 versionCode 不递增的虚假清单，确认客户端拒绝更新并给出可读提示。

**质量门槛**（与 AGENTS.md 一致）

```powershell
.\gradlew.bat :android:app:testDebugUnitTest :android:app:assembleDebug
```

---

## 15. 建议的实施顺序（可拆成独立 PR）

1. ~~**PR 1（前置修复）**：修 `versionCode` 生成方式 + CI 版本倒挂断言。~~ **已完成**（提交 `fix(build): derive versionCode from the version name`）。顺带在设置页显示 versionCode 仍未做。
2. **PR 2（纯逻辑）**：`UpdateModels` / `VersionComparator` / `UpdateManifestParser` / 资产 URL 推导 + 全部纯函数单测。无网络、无 UI，零风险。
3. **PR 3（检查）**：`GithubReleaseSource`（渠道 A/B）、`UpdateStore`、24h 节流、设置页「关于」区块 + 「检查更新」入口（此时只检查并展示结果，不下载）。
4. **PR 4（下载与安装）**：`ApkDownloader` / `ApkVerifier` / `ApkInstaller` / `UpdateInstallReceiver`、Manifest 权限与 receiver、更新对话框与进度 UI。
5. **PR 5（CI 与文档）**：`latest.json` 生成与发布、资产命名断言、README 补充说明。

每个 PR 都应跑 `testDebugUnitTest`，涉及 Manifest/资源的（PR 4、PR 5）额外跑 `assembleDebug`。PR 描述里附上手工会话的验证结果与截图（UI 改动），符合仓库的 PR 约定。

---

## 16. 待确认的开放问题

1. **触发策略**：采用 §5 的策略 A，还是仅手动检查？
2. **版本发现渠道**：是否接受为渠道 B 增加 CI 步骤与 `updates/latest.json`？还是只用渠道 A（零新增文件，但拿不到 release notes）？
3. **是否需要「更新前自动导出一次备份」**？成本很低，可作为强提示（不强制）。
4. **国内网络策略**：是否需要预留可配置的镜像/代理地址入口（默认关闭，避免硬编码第三方域名）？
5. 是否需要「跳过低版本/强制更新」语义（`minSupportedVersionCode`）？个人应用可能不需要。

---

## 附：为什么不走 Google Play

- 本项目的核心能力是读取通知（`BIND_NOTIFICATION_LISTENER_SERVICE`），上架 Play 需要额外政策说明与审核成本；
- Play 的 In-App Updates API 仅对 Play 安装的应用生效，走 Play 分发等于把更新机制交给 Play；
- 当前 keystore 自管、GitHub Release 分发的方式对个人项目更轻。若哪天改为 Play 分发，本方案的检查/下载/安装三层可整体替换为 `AppUpdateManager`，UI 与状态机基本可复用。
