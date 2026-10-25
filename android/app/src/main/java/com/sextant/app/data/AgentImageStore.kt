package com.sextant.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AgentImageUnavailableException : IllegalStateException("图片已不可用，请重新选择后发送")

/** 自有副本随对话保存；不依赖相册临时授权，也不进入账本备份。 */
class AgentImageStore(private val context: Context) {
  private val directory = File(context.noBackupFilesDir, "agent-images")

  suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取这张图片，请重新选择" }.use { stream ->
      val output = ByteArrayOutputStream()
      val buffer = ByteArray(16 * 1024)
      while (true) {
        ensureActive()
        val count = stream.read(buffer)
        if (count < 0) break
        require(output.size() + count <= MAX_SOURCE_BYTES) { "图片超过20MB，请选择较小的图片" }
        output.write(buffer, 0, count)
      }
      output.toByteArray()
    }
    val encoded = encode(bytes)
    ensureActive()
    val id = UUID.randomUUID().toString()
    AgentAtomicFile(file(id)).write(encoded)
    id
  }

  suspend fun read(id: String): ByteArray = withContext(Dispatchers.IO) {
    val source = file(id)
    if (!source.isFile || source.length() !in 1..MAX_IMAGE_BYTES.toLong()) throw AgentImageUnavailableException()
    source.readBytes()
  }

  suspend fun thumbnail(id: String, fullSize: Boolean = false): Bitmap = withContext(Dispatchers.IO) {
    val bytes = read(id)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val limit = if (fullSize) MAX_DIMENSION else 512
    options.inSampleSize = sampleSize(maxOf(options.outWidth, options.outHeight), limit)
    options.inJustDecodeBounds = false
    requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "图片无法显示" }
  }

  suspend fun removePending(id: String) = withContext(Dispatchers.IO) { file(id).delete(); Unit }

  private fun file(id: String): File {
    require(IMAGE_ID.matches(id)) { "无效的图片标识" }
    return File(directory, "$id.jpg")
  }

  private fun encode(bytes: ByteArray): ByteArray {
    fun signature(offset: Int, value: String) = bytes.size >= offset + value.length &&
      String(bytes, offset, value.length, Charsets.ISO_8859_1) == value
    require(signature(0, "\u00ff\u00d8\u00ff") || signature(0, "\u0089PNG\r\n\u001a\n") ||
      signature(0, "GIF87a") || signature(0, "GIF89a") ||
      (signature(0, "RIFF") && signature(8, "WEBP")) || signature(4, "ftyp")) {
      "图片格式无效或分辨率过大，请换一张图片"
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 100_000_000L) {
      "图片格式无效或分辨率过大，请换一张图片"
    }
    options.inSampleSize = sampleSize(maxOf(options.outWidth, options.outHeight), MAX_DIMENSION)
    options.inJustDecodeBounds = false
    val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "无法解码这张图片" }
    try {
      val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes))
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
      val transform = Matrix().apply {
        when (orientation) {
          ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
          ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
          ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
          ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
          ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
          ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
          ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
      }
      val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
      try {
        val ratio = minOf(1f, MAX_DIMENSION.toFloat() / maxOf(oriented.width, oriented.height))
        var width = (oriented.width * ratio).toInt().coerceAtLeast(1)
        var height = (oriented.height * ratio).toInt().coerceAtLeast(1)
        while (true) {
          val flattened = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
          val encoded = try {
            Canvas(flattened).apply {
              drawColor(Color.WHITE)
              drawBitmap(oriented, null, android.graphics.Rect(0, 0, width, height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
            ByteArrayOutputStream().use { output ->
              check(flattened.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "图片处理失败，请重新选择" }
              output.toByteArray()
            }
          } finally { flattened.recycle() }
          if (encoded.size <= MAX_IMAGE_BYTES) return encoded
          width = (width * 0.75f).toInt().coerceAtLeast(1)
          height = (height * 0.75f).toInt().coerceAtLeast(1)
        }
      } finally { if (oriented !== decoded) oriented.recycle() }
    } finally { decoded.recycle() }
  }

  private fun sampleSize(dimension: Int, limit: Int): Int {
    var sample = 1
    while (dimension / (sample * 2) >= limit) sample *= 2
    return sample
  }

  companion object {
    const val MAX_IMAGES = 4
    const val MAX_CONTEXT_IMAGES = 8
    private const val MAX_DIMENSION = 2048
    private const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    private val IMAGE_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
  }
}
