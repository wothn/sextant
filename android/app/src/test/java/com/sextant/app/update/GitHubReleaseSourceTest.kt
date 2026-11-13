package com.sextant.app.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class GitHubReleaseSourceTest {
  @get:Rule val temporaryFolder = TemporaryFolder()
  private val bytes = "signed apk fixture".toByteArray()
  private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
  private val apkUrl = "$RELEASE_REPOSITORY/releases/download/v0.6.0/sextant-0.6.0.apk"

  @Test fun versionCodesMatchBuildAndRejectInvalidVersions() {
    assertEquals(502L, releaseVersionCode("v0.5.2"))
    assertEquals(10000L, releaseVersionCode("1.0.0"))
    assertTrue(releaseVersionCode("0.10.0")!! > releaseVersionCode("0.9.9")!!)
    listOf("v0.6.0-rc1", "1.2", "1.100.0", "-1.0.0", "999999999999999999.0.0").forEach {
      assertNull(it, releaseVersionCode(it))
    }
  }

  @Test fun newerReleaseIncludesNotesAndExactApk() {
    val source = source()
    val release = requireNotNull(source.parseRelease(fixture(), 502))
    assertEquals("0.6.0", release.version)
    assertEquals(600L, release.versionCode)
    assertEquals("更新说明", release.notes)
    assertEquals(apkUrl, release.apkUrl)
    assertEquals(hash, release.sha256)
  }

  @Test fun currentOlderDraftAndPrereleaseAreNotOffered() {
    val source = source()
    assertNull(source.parseRelease(fixture(), 600))
    assertNull(source.parseRelease(fixture(), 601))
    assertNull(source.parseRelease(fixture().replace("\"draft\":false", "\"draft\":true"), 502))
    assertNull(source.parseRelease(fixture().replace("\"prerelease\":false", "\"prerelease\":true"), 502))
  }

  @Test fun missingApkAndInvalidDownloadOriginsAreRejected() {
    val source = source()
    assertThrows(UpdateException::class.java) { source.parseRelease(fixture().replace("sextant-0.6.0.apk", "other.apk"), 502) }
    assertThrows(UpdateException::class.java) {
      source.parseRelease(fixture().replace("https://github.com", "https://example.com"), 502)
    }
    assertThrows(UpdateException::class.java) { source.parseRelease(fixture().replace("\"size\":${bytes.size}", "\"size\":0"), 502) }
  }

  @Test fun checksumSidecarRequiredWhenDigestIsAbsent() {
    val source = source()
    val release = requireNotNull(source.parseRelease(fixture(digest = false), 502))
    assertNull(release.sha256)
    assertEquals("$apkUrl.sha256", release.checksumUrl)
    assertThrows(UpdateException::class.java) { source.parseRelease(fixture(digest = false, sidecar = false), 502) }
  }

  @Test fun rateLimitHasActionableMessageAnd404MeansNoRelease() = runBlocking {
    val limited = GitHubReleaseSource(HttpClient(MockEngine { respond("", HttpStatusCode.Forbidden) }))
    try {
      limited.latest(502)
      fail("Expected rate limit")
    } catch (error: UpdateException) {
      assertEquals("GitHub 请求受限，请稍后重试", error.message)
    }
    val missing = GitHubReleaseSource(HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }))
    assertNull(missing.latest(502))
  }

  @Test fun latestUsesPublicReleaseEndpointAndAcceptsUnknownFields() = runBlocking {
    val source = GitHubReleaseSource(HttpClient(MockEngine { request ->
      assertEquals(LATEST_RELEASE_API, request.url.toString())
      assertEquals("application/vnd.github+json", request.headers["Accept"])
      respond(fixture().replace("\"draft\":false", "\"unknown\":42,\"draft\":false"))
    }))
    assertEquals(600L, source.latest(502)?.versionCode)
  }

  @Test fun downloadValidatesDigestAndReportsCompletion() = runBlocking {
    val source = source()
    val release = requireNotNull(source.parseRelease(fixture(), 502))
    val target = temporaryFolder.newFile()
    val progress = mutableListOf<Float>()
    source.download(release, target, progress::add)
    assertArrayEquals(bytes, target.readBytes())
    assertEquals(1f, progress.last())
  }

  @Test fun downloadFallsBackToPublishedChecksumFile() = runBlocking {
    val source = source()
    val release = requireNotNull(source.parseRelease(fixture(digest = false), 502))
    val target = temporaryFolder.newFile()
    source.download(release, target) {}
    assertArrayEquals(bytes, target.readBytes())
  }

  @Test fun corruptTruncatedAndOversizedDownloadsAreDeleted() = runBlocking {
    val source = source()
    val release = requireNotNull(source.parseRelease(fixture(), 502))
    listOf(release.copy(sha256 = "0".repeat(64)), release.copy(size = bytes.size + 1L), release.copy(size = 1)).forEach {
      val target = temporaryFolder.newFile()
      try {
        source.download(it, target) {}
        fail("Expected invalid download")
      } catch (_: UpdateException) {
        assertFalse(target.exists())
      }
    }
  }

  @Test fun cancellationDeletesPartialApkAndIsNotSwallowed() = runBlocking {
    val source = source()
    val release = requireNotNull(source.parseRelease(fixture(), 502))
    val target = temporaryFolder.newFile()
    try {
      source.download(release, target) { throw CancellationException("cancelled") }
      fail("Expected cancellation")
    } catch (_: CancellationException) {
      assertFalse(target.exists())
    }
  }

  @Test fun failedDownloadDoesNotLeaveAnInstallableFile() = runBlocking {
    val source = GitHubReleaseSource(HttpClient(MockEngine { respond("unavailable", HttpStatusCode.ServiceUnavailable) }))
    val release = requireNotNull(source.parseRelease(fixture(), 502))
    val target = temporaryFolder.newFile()
    try {
      source.download(release, target) {}
      fail("Expected HTTP error")
    } catch (_: UpdateException) {
      assertFalse(target.exists())
    }
  }

  private fun source() = GitHubReleaseSource(HttpClient(MockEngine { request ->
    if (request.url.toString().endsWith(".sha256")) respond("$hash  sextant-0.6.0.apk\n") else respond(bytes)
  }))

  private fun fixture(digest: Boolean = true, sidecar: Boolean = true): String = """
    {"tag_name":"v0.6.0","body":"更新说明","draft":false,"prerelease":false,"assets":[
      {"name":"sextant-0.6.0.apk","browser_download_url":"$apkUrl","size":${bytes.size}
      ${if (digest) ",\"digest\":\"sha256:$hash\"" else ""}}
      ${if (sidecar) ",{\"name\":\"sextant-0.6.0.apk.sha256\",\"browser_download_url\":\"$apkUrl.sha256\"}" else ""}
    ]}
  """.trimIndent()
}
