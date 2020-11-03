/*
 * Copyright (C) 2020 Square, Inc.
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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.nativeHeap
import platform.posix.FILE
import platform.posix.errno
import platform.posix.fclose
import platform.posix.feof
import platform.posix.ferror
import platform.posix.fread
import platform.posix.free

/** Reads the bytes of a file as a source. */
internal class FileSource(
  private val file: CPointer<FILE>
) : Source {
  private var nativeBuffer = nativeHeap.allocArray<ByteVar>(segmentSize)
  private var closed = false

  override fun read(
    sink: Buffer,
    byteCount: Long
  ): Long {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }

    val attemptCount = minOf(byteCount, segmentSize)
    val bytesRead = fread(nativeBuffer, 1, attemptCount.toULong(), file).toLong()

    sink.write(nativeBuffer, offset = 0, byteCount = bytesRead.toInt())

    return when {
      bytesRead == attemptCount -> bytesRead
      feof(file) != 0 -> if (bytesRead == 0L) -1L else bytesRead
      ferror(file) != 0 -> throw IOException(errnoString(errno))
      else -> bytesRead
    }
  }

  override fun timeout(): Timeout = Timeout.NONE

  override fun close() {
    if (closed) return
    closed = true
    free(nativeBuffer)
    fclose(file)
  }

  companion object {
    private const val segmentSize = 8192L
  }
}
