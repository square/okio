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

import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import okio.internal.ErrnoException
import okio.internal.fdClose
import okio.internal.preview1.fd
import okio.internal.preview1.fd_filestat_get
import okio.internal.preview1.fd_filestat_set_size
import okio.internal.preview1.fd_pread
import okio.internal.preview1.fd_pwrite
import okio.internal.preview1.fd_sync
import okio.internal.read
import okio.internal.write

internal class WasiFileHandle(
  private val fd: fd,
  readWrite: Boolean,
) : FileHandle(readWrite) {
  override fun protectedSize(): Long {
    withScopedMemoryAllocator { allocator ->
      val returnPointer: Pointer = allocator.allocate(64) // filestat is 64 bytes.
      val errno = fd_filestat_get(fd, returnPointer.address.toInt())
      if (errno != 0) throw ErrnoException(errno.toShort())

      return (returnPointer + 32).loadLong()
    }
  }

  override fun protectedRead(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ): Int {
    withScopedMemoryAllocator { allocator ->
      val dataPointer = allocator.allocate(byteCount)

      val iovec = allocator.allocate(8)
      iovec.storeInt(dataPointer.address.toInt())
      (iovec + 4).storeInt(byteCount)

      val returnPointer = allocator.allocate(4) // `size` is u32, 4 bytes.
      val errno = fd_pread(
        fd = fd,
        iovs = iovec.address.toInt(),
        iovsSize = 1,
        offset = fileOffset,
        returnPointer = returnPointer.address.toInt(),
      )
      if (errno != 0) throw ErrnoException(errno.toShort())

      val readByteCount = returnPointer.loadInt()
      if (byteCount != -1) {
        dataPointer.read(array, arrayOffset, readByteCount)
      }

      if (readByteCount == 0) return -1
      return readByteCount
    }
  }

  override fun protectedWrite(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int,
  ) {
    withScopedMemoryAllocator { allocator ->
      val dataPointer = allocator.write(array, arrayOffset, byteCount)

      val iovec = allocator.allocate(8)
      iovec.storeInt(dataPointer.address.toInt())
      (iovec + 4).storeInt(byteCount)

      val returnPointer = allocator.allocate(4) // `size` is u32, 4 bytes.
      val errno = fd_pwrite(
        fd = fd,
        iovs = iovec.address.toInt(),
        iovsSize = 1,
        offset = fileOffset,
        returnPointer = returnPointer.address.toInt(),
      )
      if (errno != 0) throw ErrnoException(errno.toShort())

      val writtenByteCount = returnPointer.loadInt()
      if (writtenByteCount != byteCount) {
        throw IOException("expected $byteCount but was $writtenByteCount")
      }
    }
  }

  override fun protectedFlush() {
    val errno = fd_sync(fd)
    if (errno != 0) throw ErrnoException(errno.toShort())
  }

  override fun protectedResize(size: Long) {
    val errno = fd_filestat_set_size(fd, size)
    if (errno != 0) throw ErrnoException(errno.toShort())
  }

  override fun protectedClose() {
    fdClose(fd)
  }
}
