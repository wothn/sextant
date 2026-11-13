package com.sextant.app.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal const val RELEASE_REPOSITORY = "https://github.com/wothn/sextant"
internal const val LATEST_RELEASE_API = "https://api.github.com/repos/wothn/sextant/releases/latest"

data class AppRelease(
  val version: String,
  val versionCode: Long,
  val notes: String,
  val apkUrl: String,
  val size: Long,
  val sha256: String?,
  val checksumUrl: String?,
)

/** Keep in sync with versionCodeFrom in the app build script. Reject prerelease tags. */
internal fun releaseVersionCode(tag: String): Long? {
  val match = Regex("v?([0-9]+)\\.([0-9]{1,2})\\.([0-9]{1,2})").matchEntire(tag) ?: return null
  val major = match.groupValues[1].toLongOrNull() ?: return null
  if (major > 210_000) return null
  return major * 10_000 + match.groupValues[2].toLong() * 100 + match.groupValues[3].toLong()
}

@Serializable
private data class ReleaseResponse(
  val tag_name: String,
  val body: String? = null,
  val draft: Boolean = false,
  val prerelease: Boolean = false,
  val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
private data class ReleaseAsset(
  val name: String,
  val browser_download_url: String,
  val size: Long = 0,
  val digest: String? = null,
  val state: String = "uploaded",
)

internal class UpdateException(message: String) : IOException(message)

internal class GitHubReleaseSource(
  private val client: HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
      connectTimeoutMillis = 20_000
      socketTimeoutMillis = 30_000
    }
  },
) {
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun latest(currentVersionCode: Long): AppRelease? = withContext(Dispatchers.IO) {
    val response = client.get(LATEST_RELEASE_API) {
      header("Accept", "application/vnd.github+json")
      header("X-GitHub-Api-Version", "2022-11-28")
      header("User-Agent", "Sextant-Android")
    }
    when (response.status) {
      HttpStatusCode.NotFound -> return@withContext null
      HttpStatusCode.Forbidden, HttpStatusCode.TooManyRequests ->
        throw UpdateException("GitHub 请求受限，请稍后重试")
    }
    requireSuccess(response.status)
    parseRelease(response.bodyAsText(), currentVersionCode)
  }

  internal fun parseRelease(body: String, currentVersionCode: Long): AppRelease? {
    val release = json.decodeFromString<ReleaseResponse>(body)
    if (release.draft || release.prerelease) return null
    val code = releaseVersionCode(release.tag_name) ?: throw UpdateException("发布版本号格式不受支持")
    if (code <= currentVersionCode) return null
    val version = release.tag_name.removePrefix("v")
    val name = "sextant-$version.apk"
    val apk = release.assets.singleOrNull { it.name == name && it.state == "uploaded" }
      ?: throw UpdateException("新版本尚未提供可下载的安装包，请稍后重试")
    validateAssetUrl(apk.browser_download_url, release.tag_name, name)
    if (apk.size <= 0 || apk.size > MAX_APK_BYTES) throw UpdateException("安装包大小异常")
    val digest = apk.digest?.removePrefix("sha256:")?.takeIf { HASH.matches(it) }
    val checksum = release.assets.singleOrNull { it.name == "$name.sha256" && it.state == "uploaded" }
    checksum?.let { validateAssetUrl(it.browser_download_url, release.tag_name, "$name.sha256") }
    if (digest == null && checksum == null) throw UpdateException("发布缺少 SHA-256 校验信息")
    return AppRelease(version, code, release.body.orEmpty(), apk.browser_download_url, apk.size,
      digest, checksum?.browser_download_url)
  }

  suspend fun download(release: AppRelease, target: File, progress: (Float) -> Unit): File =
    withContext(Dispatchers.IO) {
      try {
        val expectedHash = release.sha256 ?: run {
          val response = client.get(requireNotNull(release.checksumUrl))
          requireSuccess(response.status)
          response.bodyAsText().trim().split(Regex("\\s+"), limit = 2).first()
            .takeIf { HASH.matches(it) } ?: throw UpdateException("SHA-256 校验文件无效")
        }
        target.parentFile?.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        client.prepareGet(release.apkUrl).execute { response ->
          requireSuccess(response.status)
          val channel = response.bodyAsChannel()
          val buffer = ByteArray(64 * 1024)
          var received = 0L
          var reported = -1
          target.outputStream().use { output ->
            while (true) {
              currentCoroutineContext().ensureActive()
              val count = channel.readAvailable(buffer, 0, buffer.size)
              if (count == -1) break
              received += count
              if (received > release.size) throw UpdateException("安装包大小与发布信息不符，请重新下载")
              output.write(buffer, 0, count)
              digest.update(buffer, 0, count)
              val percent = (received * 100 / release.size).toInt()
              if (percent != reported) {
                progress(received.toFloat() / release.size)
                reported = percent
              }
            }
          }
          if (received != release.size) throw UpdateException("安装包下载不完整，请重新下载")
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualHash.equals(expectedHash, ignoreCase = true)) {
          throw UpdateException("安装包校验失败，请重新下载")
        }
        target
      } catch (error: Exception) {
        target.delete()
        throw error
      }
    }

  private fun validateAssetUrl(url: String, tag: String, name: String) {
    if (url != "$RELEASE_REPOSITORY/releases/download/$tag/$name") {
      throw UpdateException("发布附件地址无效")
    }
  }

  private fun requireSuccess(status: HttpStatusCode) {
    if (status.value !in 200..299) throw UpdateException("GitHub 请求失败（${status.value}），请稍后重试")
  }

  private companion object {
    val HASH = Regex("[a-fA-F0-9]{64}")
    const val MAX_APK_BYTES = 512L * 1024 * 1024
  }
}
