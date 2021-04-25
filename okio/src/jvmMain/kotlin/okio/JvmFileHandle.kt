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

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

@ExperimentalFileSystem
internal class JvmFileHandle(
  private val fileChannel: FileChannel
) : FileHandle() {
  override fun resize(size: Long) {
    val currentSize = size()
    val delta = size - currentSize
    if (delta > 0) {
      protectedWrite(currentSize, ByteArray(delta.toInt()), 0, delta.toInt())
    } else {
      fileChannel.truncate(size)
    }
  }

  override fun size(): Long {
    return fileChannel.size()
  }

  override fun protectedRead(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int
  ): Int {
    return fileChannel.read(ByteBuffer.wrap(array, arrayOffset, byteCount), fileOffset)
  }

  override fun protectedWrite(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int
  ) {
    fileChannel.write(ByteBuffer.wrap(array, arrayOffset, byteCount), fileOffset)
  }

  override fun protectedFlush() {
    fileChannel.force(false)
  }

  override fun protectedClose() {
    fileChannel.close()
  }
}
