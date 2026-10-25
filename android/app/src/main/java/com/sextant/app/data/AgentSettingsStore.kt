package com.sextant.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AgentSettings(val apiKey: String = "", val model: String = "deepseek-flash")

/** Key 只存于 noBackup 私有目录，使用 Android Keystore AES/GCM 加密。 */
class AgentSettingsStore(context: Context) {
  private val file = AgentAtomicFile(File(context.noBackupFilesDir, "agent-settings.enc"))
  private val mutex = Mutex()

  suspend fun read(): AgentSettings = mutex.withLock { withContext(Dispatchers.IO) {
    if (!file.exists()) return@withContext AgentSettings()
    val parts = String(file.read(), Charsets.UTF_8).split(':', limit = 2)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
    Json.decodeFromString<AgentSettings>(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
  } }

  suspend fun save(settings: AgentSettings) = mutex.withLock { withContext(Dispatchers.IO) {
    require(settings.model.isNotBlank() && settings.model.length <= 100) { "请填写有效的模型名称" }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val encrypted = cipher.doFinal(Json.encodeToString(AgentSettings.serializer(), settings.copy(apiKey = settings.apiKey.trim(), model = settings.model.trim())).toByteArray(Charsets.UTF_8))
    file.write((Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).toByteArray())
  } }

  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey("sextant-agent-v1", null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
      init(KeyGenParameterSpec.Builder("sextant-agent-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
    }.generateKey()
  }
}
