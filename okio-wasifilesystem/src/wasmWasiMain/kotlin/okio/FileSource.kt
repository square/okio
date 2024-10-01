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

import kotlin.wasm.unsafe.withScopedMemoryAllocator
import okio.internal.ErrnoException
import okio.internal.fdClose
import okio.internal.preview1.fd
import okio.internal.preview1.fd_read
import okio.internal.preview1.size
import okio.internal.read

internal class FileSource(
  private val fd: fd,
) : Source {
  private val unsafeCursor = Buffer.UnsafeCursor()
  private var closed = false

  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    val sinkInitialSize = sink.size

    // Request a writable segment in `sink`. We request at least 1024 bytes, unless the request is
    // for smaller than that, in which case we request only that many bytes.
    val cursor = sink.readAndWriteUnsafe(unsafeCursor)
    val addedCapacityCount = cursor.expandBuffer(minByteCount = minOf(byteCount, 1024L).toInt())

    // Now that we have a writable segment, figure out how many bytes to read. This is the smaller
    // of the user's requested byte count, and the segment's writable capacity.
    val attemptCount = minOf(byteCount, addedCapacityCount).toInt()

    // Copy bytes from the file to the segment.
    val bytesRead = fdRead(cursor.data!!, cursor.start, attemptCount)

    // Remove new capacity that was added but not used.
    cursor.resizeBuffer(sinkInitialSize + bytesRead)
    cursor.close()

    return when {
      bytesRead == attemptCount -> bytesRead.toLong()
      else -> if (bytesRead == 0) -1L else bytesRead.toLong()
    }
  }

  private fun fdRead(data: ByteArray, offset: Int, count: Int): size {
    withScopedMemoryAllocator { allocator ->
      val dataPointer = allocator.allocate(count)

      val iovec = allocator.allocate(8)
      iovec.storeInt(dataPointer.address.toInt())
      (iovec + 4).storeInt(count)

      val returnPointer = allocator.allocate(4) // `size` is u32, 4 bytes.
      val errno = fd_read(
        fd = fd,
        iovs = iovec.address.toInt(),
        iovsSize = 1,
        returnPointer = returnPointer.address.toInt(),
      )
      if (errno != 0) throw ErrnoException(errno.toShort())

      val byteCount = returnPointer.loadInt()
      if (byteCount != -1) {
        dataPointer.read(data, offset, byteCount)
      }

      return byteCount
    }
  }

  override fun timeout(): Timeout = Timeout.NONE

  override fun close() {
    if (closed) return
    closed = true
    fdClose(fd)
  }
}
