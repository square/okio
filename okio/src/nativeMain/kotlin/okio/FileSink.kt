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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import okio.Buffer.UnsafeCursor
import platform.posix.FILE
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fflush

/** Writes bytes to a file as a sink. */
internal class FileSink(
  private val file: CPointer<FILE>,
) : Sink {
  private val unsafeCursor = UnsafeCursor()
  private var closed = false

  override fun write(
    source: Buffer,
    byteCount: Long,
  ) {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    require(source.size >= byteCount) { "source.size=${source.size} < byteCount=$byteCount" }
    check(!closed) { "closed" }

    var byteCount = byteCount
    while (byteCount > 0) {
      // Get the first segment, which we will read a contiguous range of bytes from.
      val cursor = source.readUnsafe(unsafeCursor)
      val segmentReadableByteCount = cursor.next()
      val attemptCount = minOf(byteCount, segmentReadableByteCount.toLong()).toInt()

      // Copy bytes from that segment into the file.
      val bytesWritten = cursor.data!!.usePinned { pinned ->
        variantFwrite(pinned.addressOf(cursor.start), attemptCount.toUInt(), file).toLong()
      }

      // Consume the bytes from the segment.
      cursor.close()
      source.skip(bytesWritten)
      byteCount -= bytesWritten

      // If the write was shorter than expected, some I/O failed.
      if (bytesWritten < attemptCount) {
        throw errnoToIOException(errno)
      }
    }
  }

  override fun flush() {
    if (fflush(file) != 0) {
      throw errnoToIOException(errno)
    }
  }

  override fun timeout(): Timeout = Timeout.NONE

  override fun close() {
    if (closed) return
    closed = true
    if (fclose(file) != 0) {
      throw errnoToIOException(errno)
    }
  }
}
