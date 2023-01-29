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
import platform.posix.feof
import platform.posix.ferror

/** Reads the bytes of a file as a source. */
internal class FileSource(
  private val file: CPointer<FILE>,
) : Source {
  private val unsafeCursor = UnsafeCursor()
  private var closed = false

  override fun read(
    sink: Buffer,
    byteCount: Long,
  ): Long {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    val sinkInitialSize = sink.size

    // Request a writable segment in `sink`. We request at least 1024 bytes, unless the request is
    // for smaller than that, in which case we request only that many bytes.
    val cursor = sink.readAndWriteUnsafe(unsafeCursor)
    val addedCapacityCount = cursor.expandBuffer(minByteCount = minOf(byteCount, 1024L).toInt())

    // Now that we have a writable segment, figure out how many bytes to read. This is the smaller
    // of the user's requested byte count, and the segment's writable capacity.
    val attemptCount = minOf(byteCount, addedCapacityCount)

    // Copy bytes from the file to the segment.
    val bytesRead = cursor.data!!.usePinned { pinned ->
      variantFread(pinned.addressOf(cursor.start), attemptCount.toUInt(), file).toLong()
    }

    // Remove new capacity that was added but not used.
    cursor.resizeBuffer(sinkInitialSize + bytesRead)
    cursor.close()

    return when {
      bytesRead == attemptCount -> bytesRead
      feof(file) != 0 -> if (bytesRead == 0L) -1L else bytesRead
      ferror(file) != 0 -> throw errnoToIOException(errno)
      else -> bytesRead
    }
  }

  override fun timeout(): Timeout = Timeout.NONE

  override fun close() {
    if (closed) return
    closed = true
    fclose(file)
  }
}
