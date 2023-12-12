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
import okio.internal.preview1.fd_sync
import okio.internal.preview1.fd_write
import okio.internal.preview1.size
import okio.internal.write

internal class FileSink(
  private val fd: fd,
) : Sink {
  private var closed = false
  private val cursor = Buffer.UnsafeCursor()

  override fun write(source: Buffer, byteCount: Long) {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    require(source.size >= byteCount) { "source.size=${source.size} < byteCount=$byteCount" }
    check(!closed) { "closed" }

    var bytesRemaining = byteCount
    source.readAndWriteUnsafe(cursor)
    try {
      while (bytesRemaining > 0L) {
        check(cursor.next() != -1)

        val count = minOf(bytesRemaining, cursor.end.toLong() - cursor.start).toInt()
        if (fdWrite(cursor.data!!, cursor.start, count) != count) {
          throw IOException("write failed")
        }
        bytesRemaining -= count
      }
    } finally {
      cursor.close()
      source.skip(byteCount)
    }
  }

  private fun fdWrite(data: ByteArray, offset: Int, count: Int): size {
    withScopedMemoryAllocator { allocator ->
      val dataPointer = allocator.write(data, offset, count)

      val iovec = allocator.allocate(8)
      iovec.storeInt(dataPointer.address.toInt())
      (iovec + 4).storeInt(count)

      val returnPointer = allocator.allocate(4) // `size` is u32, 4 bytes.
      val errno = fd_write(
        fd = fd,
        iovs = iovec.address.toInt(),
        iovsSize = 1,
        returnPointer = returnPointer.address.toInt(),
      )
      if (errno != 0) throw ErrnoException(errno.toShort())

      return returnPointer.loadInt()
    }
  }

  override fun flush() {
    val errno = fd_sync(fd)
    if (errno != 0) throw ErrnoException(errno.toShort())
  }

  override fun timeout() = Timeout.NONE

  override fun close() {
    if (closed) return
    closed = true
    fdClose(fd)
  }
}
