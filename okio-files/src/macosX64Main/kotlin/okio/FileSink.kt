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
import platform.posix.fflush
import platform.posix.free
import platform.posix.fwrite

/** Writes bytes to a file as a sink. */
internal class FileSink(
  private val file: CPointer<FILE>
) : Sink {
  private var nativeBuffer = nativeHeap.allocArray<ByteVar>(segmentSize)
  private var closed = false

  override fun write(
    source: Buffer,
    byteCount: Long
  ) {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }

    var byteCount = byteCount
    while (byteCount > 0) {
      val attemptCount = minOf(byteCount, segmentSize).toInt()
      source.read(nativeBuffer, offset = 0, byteCount = attemptCount)
      val bytesWritten = fwrite(nativeBuffer, 1, attemptCount.toULong(), file).toInt()
      if (bytesWritten < attemptCount) {
        throw IOException(errnoString(errno))
      }
      byteCount -= bytesWritten
    }
  }

  override fun flush() {
    if (fflush(file) != 0) {
      throw IOException(errnoString(errno))
    }
  }

  override fun timeout(): Timeout = Timeout.NONE

  override fun close() {
    if (closed) return
    closed = true
    free(nativeBuffer)
    if (fclose(file) != 0) {
      throw IOException(errnoString(errno))
    }
  }

  companion object {
    private const val segmentSize = 8192L
  }
}
