package com.sextant.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class AgentDocument(val id: String, val name: String, val upload: AgentDocumentUpload? = null)

@Serializable
data class AgentDocumentUpload(val fileId: String, val expiresAt: Long, val keyFingerprint: String)

class AgentDocumentStore(private val context: Context) {
  private val directory = File(context.noBackupFilesDir, "agent-documents")

  suspend fun import(uri: Uri): AgentDocument = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }.orEmpty()
    val extension = name.substringAfterLast('.', "").lowercase()
    val limit = maxBytes(extension)
    require(limit > 0) { "请选择 PDF、Office、CSV、文本或电子书文件" }
    val displayName = name.substringBeforeLast('.').filterNot(Char::isISOControl).take(120) + ".$extension"
    val id = UUID.randomUUID().toString()
    val target = file(id)
    check(directory.isDirectory || directory.mkdirs()) { "无法准备附件存储" }
    var complete = false
    try {
      requireNotNull(resolver.openInputStream(uri)) { "无法读取附件，请重新选择" }.use { input ->
        target.outputStream().buffered().use { output ->
          val buffer = ByteArray(64 * 1024)
          var size = 0L
          while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            require(size <= limit) { "附件超过 ${limit / MIB} MiB" }
            output.write(buffer, 0, count)
          }
          require(size > 0) { "无法发送空文件" }
        }
      }
      ensureActive()
      complete = true
      AgentDocument(id, displayName)
    } finally {
      if (!complete) target.delete()
    }
  }

  internal fun source(document: AgentDocument): File {
    val source = file(document.id)
    val limit = maxBytes(document.name.substringAfterLast('.', ""))
    require(source.isFile && source.length() in 1..limit) { "附件已不可用，请重新选择后发送" }
    return source
  }

  suspend fun removePending(id: String) = withContext(Dispatchers.IO) { file(id).delete(); Unit }

  private fun file(id: String): File {
    require(UUID.fromString(id).toString() == id) { "无效的附件标识" }
    return File(directory, id)
  }

  companion object {
    const val MAX_FILES = 4
    private const val MIB = 1024L * 1024
    private val scanned = setOf("pdf", "doc", "docx", "ppt", "pptx")
    private val other = setOf("xls", "xlsx", "csv", "txt", "md", "json", "epub", "mobi")

    internal fun purpose(name: String): String =
      if (name.substringAfterLast('.') in scanned) "text_recognition" else "user_data"

    private fun maxBytes(extension: String): Long = when (extension) {
      in scanned -> 100 * MIB
      in other -> 32 * MIB
      else -> 0
    }
  }
}
