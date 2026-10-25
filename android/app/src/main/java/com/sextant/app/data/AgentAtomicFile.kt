package com.sextant.app.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Android 26+ 和本地 JVM 使用同一条原子替换路径；替换失败必须抛出，不能静默保留旧数据。 */
internal class AgentAtomicFile(val baseFile: File) {
  fun exists(): Boolean = baseFile.exists()
  fun read(): ByteArray = baseFile.readBytes()
  fun write(bytes: ByteArray) {
    baseFile.parentFile?.mkdirs()
    val temporary = File(baseFile.path + ".pending")
    try {
      FileOutputStream(temporary).use { stream -> stream.write(bytes); stream.fd.sync() }
      Files.move(temporary.toPath(), baseFile.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } finally { temporary.delete() }
  }
}
