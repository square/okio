/*
 * Copyright (C) 2021 Square, Inc.
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

import java.io.RandomAccessFile

internal class JvmFileHandle(
  readWrite: Boolean,
  private val randomAccessFile: RandomAccessFile,
) : FileHandle(readWrite) {

  @Synchronized
  override fun protectedResize(size: Long) {
    val currentSize = size()
    val delta = size - currentSize
    if (delta > 0) {
      protectedWrite(currentSize, ByteArray(delta.toInt()), 0, delta.toInt())
    } else {
      randomAccessFile.setLength(size)
    }
  }

  @Synchronized
  override fun protectedSize(): Long {
    return randomAccessFile.length()
  }

  @Synchronized
  override fun protectedRead(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ): Int {
    randomAccessFile.seek(fileOffset)
    var bytesRead = 0
    while (bytesRead < byteCount) {
      val readResult = randomAccessFile.read(array, arrayOffset, byteCount - bytesRead)
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
    randomAccessFile.seek(fileOffset)
    randomAccessFile.write(array, arrayOffset, byteCount)
  }

  @Synchronized
  override fun protectedFlush() {
    randomAccessFile.fd.sync()
  }

  @Synchronized
  override fun protectedClose() {
    randomAccessFile.close()
  }
}
