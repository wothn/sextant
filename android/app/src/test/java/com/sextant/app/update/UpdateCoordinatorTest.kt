package com.sextant.app.update

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateCoordinatorTest {
  private lateinit var context: Context
  private val calls = AtomicInteger()

  @Before fun setUp() {
    val application = RuntimeEnvironment.getApplication()
    @Suppress("DEPRECATION")
    val info = PackageInfo().apply {
      packageName = "com.sextant.app"
      versionName = "0.5.2"
      versionCode = 502
    }
    shadowOf(application.packageManager).installPackage(info)
    context = object : ContextWrapper(application) {
      override fun getPackageName() = "com.sextant.app"
    }
    context.getSharedPreferences("app_updates", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test fun automaticChecksAreThrottledAcrossCoordinatorInstancesButManualChecksBypassCache() = runBlocking {
    val coordinator = coordinator(this)
    coordinator.check(automatic = true)
    awaitCheck(coordinator)
    assertEquals(1, calls.get())
    coordinator.check(automatic = true)
    val recreated = coordinator(this)
    recreated.check(automatic = true)
    assertEquals(1, calls.get())
    recreated.check()
    awaitCheck(recreated)
    assertEquals(2, calls.get())
    assertEquals("当前已是最新版本", recreated.state.value.message)
  }

  @Test fun simultaneousChecksShareOneRequest() = runBlocking {
    val coordinator = coordinator(this)
    coordinator.check()
    coordinator.check()
    awaitCheck(coordinator)
    assertEquals(1, calls.get())
  }

  @Test fun automaticFailureIsQuietAndRetryIsThrottledWhileManualFailureIsVisible() = runBlocking {
    val coordinator = coordinator(this, HttpStatusCode.Forbidden)
    coordinator.check(automatic = true)
    awaitCheck(coordinator)
    assertNull(coordinator.state.value.message)
    coordinator.check(automatic = true)
    val recreated = coordinator(this, HttpStatusCode.Forbidden)
    recreated.check(automatic = true)
    assertEquals(1, calls.get())
    coordinator.check()
    awaitCheck(coordinator)
    assertEquals(2, calls.get())
    assertEquals("GitHub 请求受限，请稍后重试", coordinator.state.value.message)
  }

  @Test fun debugPackageDoesNotAttemptReleaseInstallationOrNetworkCheck() = runBlocking {
    val application = RuntimeEnvironment.getApplication()
    val coordinator = UpdateCoordinator(application, this, source(HttpStatusCode.OK))
    assertFalse(coordinator.supported)
    coordinator.check(automatic = true)
    assertNull(coordinator.state.value.message)
    coordinator.check()
    assertEquals(0, calls.get())
    assertEquals("调试版不支持覆盖安装正式版，请使用 GitHub Release 正式版", coordinator.state.value.message)
    assertNull(coordinator.installerIntent())
  }

  @Test fun updateProviderOnlyGrantsAccessToUpdateCacheDirectory() {
    val application = RuntimeEnvironment.getApplication()
    val authority = "${application.packageName}.updates"
    val provider = requireNotNull(application.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA))
    assertFalse(provider.exported)
    assertTrue(provider.grantUriPermissions)
    // FileProvider uses Android '/' paths internally; inspect its access policy directly so this
    // also runs under Windows Robolectric, where java.io.File uses backslashes.
    val paths = mutableListOf<Pair<String, String?>>()
    requireNotNull(provider.loadXmlMetaData(application.packageManager, "android.support.FILE_PROVIDER_PATHS")).use { xml ->
      while (xml.next() != XmlPullParser.END_DOCUMENT) {
        if (xml.eventType == XmlPullParser.START_TAG && xml.name != "paths") {
          paths += xml.name to xml.getAttributeValue(null, "path")
        }
      }
    }
    assertEquals(listOf("cache-path" to "updates/"), paths)
  }

  private fun coordinator(scope: CoroutineScope, status: HttpStatusCode = HttpStatusCode.NotFound) =
    UpdateCoordinator(context, scope, source(status))

  private fun source(status: HttpStatusCode) = GitHubReleaseSource(HttpClient(MockEngine {
    calls.incrementAndGet()
    respond("", status)
  }))

  private suspend fun awaitCheck(coordinator: UpdateCoordinator) {
    withTimeout(10_000) { coordinator.state.first { !it.checking } }
  }
}
