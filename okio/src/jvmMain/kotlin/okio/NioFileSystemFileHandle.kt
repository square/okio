/*
 * Copyright (C) 2023 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class NioFileSystemFileHandle(
  readWrite: Boolean,
  private val fileChannel: FileChannel,
) : FileHandle(readWrite) {

  @Synchronized
  override fun protectedResize(size: Long) {
    val currentSize = size()
    val delta = size - currentSize
    if (delta > 0) {
      protectedWrite(currentSize, ByteArray(delta.toInt()), 0, delta.toInt())
    } else {
      fileChannel.truncate(size)
    }
  }

  @Synchronized
  override fun protectedSize(): Long {
    return fileChannel.size()
  }

  @Synchronized
  override fun protectedRead(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ): Int {
    fileChannel.position(fileOffset)
    val byteBuffer = ByteBuffer.wrap(array, arrayOffset, byteCount)
    var bytesRead = 0
    while (bytesRead < byteCount) {
      val readResult = fileChannel.read(byteBuffer)
      if (readResult == -1) {
        if (bytesRead == 0) return -1
        break
      }
      bytesRead += readResult
    }
    return bytesRead
  }

  @Synchronized
  override fun protectedWrite(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ) {
    fileChannel.position(fileOffset)
    val byteBuffer = ByteBuffer.wrap(array, arrayOffset, byteCount)
    fileChannel.write(byteBuffer)
  }

  @Synchronized
  override fun protectedFlush() {
    fileChannel.force(true)
  }

  @Synchronized
  override fun protectedClose() {
    fileChannel.close()
  }
}
