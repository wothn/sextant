package com.sextant.app.automation

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

class AutomationSecretStore(context: Context) {
  private val credentialFile = File(context.noBackupFilesDir, CREDENTIAL_FILE_NAME)

  fun hasApiKey(): Boolean = credentialFile.isFile && credentialFile.length() > 0

  fun setApiKey(apiKey: String) {
    val trimmed = apiKey.trim()
    if (trimmed.isBlank()) {
      clearApiKey()
      return
    }
    val encrypted = encrypt(trimmed.toByteArray(Charsets.UTF_8))
    credentialFile.writeText(
      Base64.encodeToString(encrypted.iv, Base64.NO_WRAP) + "\n" +
        Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP),
      Charsets.UTF_8,
    )
  }

  fun getApiKey(): String? {
    if (!credentialFile.isFile) return null
    return runCatching {
      val parts = credentialFile.readLines(Charsets.UTF_8)
      require(parts.size == 2)
      val value = decrypt(
        EncryptedValue(
          ciphertext = Base64.decode(parts[1], Base64.NO_WRAP),
          iv = Base64.decode(parts[0], Base64.NO_WRAP),
        ),
      )
      value.toString(Charsets.UTF_8)
    }.getOrElse {
      clearApiKey()
      null
    }
  }

  fun clearApiKey() {
    if (credentialFile.exists()) credentialFile.delete()
  }

  fun encrypt(value: ByteArray): EncryptedValue {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    return EncryptedValue(cipher.doFinal(value), cipher.iv)
  }

  fun decrypt(value: EncryptedValue): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, value.iv))
    return cipher.doFinal(value.ciphertext)
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build(),
    )
    return generator.generateKey()
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "sextant-automation-v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_LENGTH = 128
    const val CREDENTIAL_FILE_NAME = "automation-api-key"
  }
}
