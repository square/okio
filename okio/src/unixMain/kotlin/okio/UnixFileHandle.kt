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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.posix.FILE
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fstat
import platform.posix.ftruncate
import platform.posix.stat

@ExperimentalFileSystem
internal class UnixFileHandle(
  readWrite: Boolean,
  private val file: CPointer<FILE>
) : FileHandle(readWrite) {
  override fun protectedSize(): Long {
    memScoped {
      val stat = alloc<stat>()
      if (fstat(fileno(file), stat.ptr) != 0) {
        throw errnoToIOException(errno)
      }
      return stat.st_size
    }
  }

  override fun protectedRead(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int
  ): Int {
    val bytesRead = array.usePinned { pinned ->
      variantPread(file, pinned.addressOf(arrayOffset), byteCount, fileOffset)
    }
    if (bytesRead == -1) throw errnoToIOException(errno)
    if (bytesRead == 0) return -1
    return bytesRead
  }

  override fun protectedWrite(
    fileOffset: Long,
    array: ByteArray,
    arrayOffset: Int,
    byteCount: Int
  ) {
    val bytesWritten = array.usePinned { pinned ->
      variantPwrite(file, pinned.addressOf(arrayOffset), byteCount, fileOffset)
    }
    if (bytesWritten != byteCount) throw errnoToIOException(errno)
  }

  override fun protectedFlush() {
    if (fflush(file) != 0) {
      throw errnoToIOException(errno)
    }
  }

  override fun protectedResize(size: Long) {
    if (ftruncate(fileno(file), size) == -1) {
      throw errnoToIOException(errno)
    }
  }

  override fun protectedClose() {
    if (fclose(file) != 0) {
      throw errnoToIOException(errno)
    }
  }
}
