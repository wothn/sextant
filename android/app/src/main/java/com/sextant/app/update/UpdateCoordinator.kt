package com.sextant.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

data class UpdateState(
  val checking: Boolean = false,
  val downloading: Boolean = false,
  val progress: Float = 0f,
  val release: AppRelease? = null,
  val ready: Boolean = false,
  val showDialog: Boolean = false,
  val message: String? = null,
) {
  val busy: Boolean get() = checking || downloading
}

/** Application-scoped work survives navigation and Activity recreation. Installation remains explicit. */
class UpdateCoordinator internal constructor(
  private val context: Context,
  private val scope: CoroutineScope,
  private val source: GitHubReleaseSource = GitHubReleaseSource(),
) {
  private val preferences = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
  private val mutableState = MutableStateFlow(UpdateState())
  val state = mutableState.asStateFlow()
  @Suppress("DEPRECATION")
  private val installed = context.packageManager.getPackageInfo(context.packageName, 0)
  val currentVersion: String = installed.versionName.orEmpty()
  val supported: Boolean = context.packageName == "com.sextant.app"
  private val apkFile get() = File(context.cacheDir, "updates/sextant-update.apk")

  fun check(automatic: Boolean = false) {
    if (mutableState.value.busy) return
    if (!supported) {
      if (!automatic) mutableState.update { it.copy(message = "调试版不支持覆盖安装正式版，请使用 GitHub Release 正式版") }
      return
    }
    val now = System.currentTimeMillis()
    if (automatic) {
      val lastSuccess = preferences.getLong("last_check", 0)
      val lastAutomaticAttempt = preferences.getLong("last_automatic_attempt", 0)
      if (now - lastAutomaticAttempt in 0 until RETRY_INTERVAL || now - lastSuccess in 0 until CHECK_INTERVAL) return
      if (mutableState.value.release != null) return
      preferences.edit().putLong("last_automatic_attempt", now).apply()
    }
    mutableState.update { it.copy(checking = true, message = null) }
    scope.launch {
      try {
        val release = source.latest(PackageInfoCompat.getLongVersionCode(installed))
        preferences.edit().putLong("last_check", now).apply()
        mutableState.update {
          it.copy(checking = false, release = release, ready = it.ready && it.release == release && apkFile.exists(),
            showDialog = release != null, message = if (release == null && !automatic) "当前已是最新版本" else null)
        }
      } catch (error: CancellationException) {
        mutableState.update { it.copy(checking = false) }
        throw error
      } catch (error: Exception) {
        mutableState.update { it.copy(checking = false, message = if (automatic) null else error.userMessage()) }
      }
    }
  }

  fun showRelease() { mutableState.update { it.copy(showDialog = it.release != null, message = null) } }
  fun dismiss() { mutableState.update { it.copy(showDialog = false, message = null) } }
  fun dismissMessage() { mutableState.update { it.copy(message = null) } }

  fun download() {
    val release = mutableState.value.release ?: return
    if (mutableState.value.busy) return
    mutableState.update { it.copy(downloading = true, ready = false, progress = 0f, message = null) }
    scope.launch {
      try {
        source.download(release, apkFile) { progress -> mutableState.update { it.copy(progress = progress) } }
        withContext(Dispatchers.IO) { validateApk(apkFile, release) }
        mutableState.update { it.copy(downloading = false, ready = true) }
      } catch (error: CancellationException) {
        apkFile.delete()
        mutableState.update { it.copy(downloading = false) }
        throw error
      } catch (error: Exception) {
        apkFile.delete()
        mutableState.update { it.copy(downloading = false, message = error.userMessage()) }
      }
    }
  }

  fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

  fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

  fun installerIntent(): Intent? {
    if (!mutableState.value.ready) return null
    if (!apkFile.exists()) {
      mutableState.update { it.copy(ready = false, message = "安装包已被系统清理，请重新下载") }
      return null
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apkFile)
    return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }

  fun installationError() {
    mutableState.update { it.copy(message = "无法打开安装界面，请检查系统安装权限后重试") }
  }

  @Suppress("DEPRECATION")
  private fun validateApk(file: File, release: AppRelease) {
    val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
      ?: throw UpdateException("安装包无法解析，请重新下载")
    val current = context.packageManager.getPackageInfo(context.packageName, flags)
    if (archive.packageName != context.packageName || PackageInfoCompat.getLongVersionCode(archive) != release.versionCode ||
      PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(current)) {
      throw UpdateException("安装包的应用或版本不匹配")
    }
    val currentSigners = current.signers(history = false)
    val incomingSigners = archive.signers(history = true)
    if (currentSigners.isEmpty() || !incomingSigners.containsAll(currentSigners)) {
      throw UpdateException("安装包签名与当前应用不一致，无法覆盖更新")
    }
  }

  @Suppress("DEPRECATION")
  private fun PackageInfo.signers(history: Boolean): Set<String> {
    val certificates = if (Build.VERSION.SDK_INT >= 28) {
      signingInfo?.let { if (history && !it.hasMultipleSigners()) it.signingCertificateHistory else it.apkContentsSigners }
    } else signatures
    return certificates.orEmpty().map { it.toCharsString() }.toSet()
  }

  private fun Exception.userMessage() = if (this is UpdateException) message.orEmpty() else "更新失败，请检查网络和存储空间后重试"

  private companion object {
    const val CHECK_INTERVAL = 24 * 60 * 60 * 1000L
    const val RETRY_INTERVAL = 60 * 60 * 1000L
  }
}
