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

internal class NodeJsFileHandle(
  private val fd: Number,
  readWrite: Boolean,
) : FileHandle(readWrite) {
  override fun protectedSize(): Long {
    val stats = fstatSync(fd)
    return stats.size.toLong()
  }

  override fun protectedRead(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ): Int {
    val readByteCount = readSync(
      fd = fd,
      buffer = array,
      length = byteCount.toDouble(),
      offset = arrayOffset.toDouble(),
      position = fileOffset.toDouble(),
    ).toInt()

    if (readByteCount == 0) return -1

    return readByteCount
  }

  override fun protectedWrite(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ) {
    val writtenByteCount = writeSync(
      fd = fd,
      buffer = array,
      offset = arrayOffset.toDouble(),
      length = byteCount.toDouble(),
      position = fileOffset.toDouble(),
    )

    if (writtenByteCount.toInt() != byteCount) {
      throw IOException("expected $byteCount but was $writtenByteCount")
    }
  }

  override fun protectedFlush() {
  }

  override fun protectedResize(size: Long) {
    ftruncateSync(fd, size.toDouble())
  }

  override fun protectedClose() {
    closeSync(fd)
  }
}
