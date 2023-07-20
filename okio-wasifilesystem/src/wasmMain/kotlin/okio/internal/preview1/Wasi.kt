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

@file:OptIn(UnsafeWasmMemoryApi::class)

package okio.internal.preview1

import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import okio.Path.Companion.toPath


internal fun pathOpen(path: String, oflags: oflags, rightsBase: rights): fd {
  withScopedMemoryAllocator { allocator ->
    val (pathAddress, pathSize) = allocator.write(path)

    val returnPointer: Pointer = allocator.allocate(4) // fd is u32.
    val errno = path_open(
      fd = UnspecifiedFd,
      dirflags = 0,
      path = pathAddress.address.toInt(),
      pathSize = pathSize,
      oflags = oflags,
      fs_rights_base = rightsBase,
      fs_rights_inheriting = 0,
      fdflags = 0,
      returnPointer = returnPointer.address.toInt(),
    )
    if (errno != 0) throw ErrnoException(errno.toShort())
    return returnPointer.loadInt()
  }
}

@OptIn(UnsafeWasmMemoryApi::class)
internal fun fdReadDir(fd: fd): List<DirectoryEntry> {
  withScopedMemoryAllocator { allocator ->
    var bufSize = 2048
    var bufPointer = allocator.allocate(bufSize)
    val returnPointer = allocator.allocate(4) // `size` is u32, 4 bytes.
    var pageSize: Int

    // In theory, fd_readdir uses a 'cookie' field to page through results. In practice the NodeJS
    // implementation doesn't honor the cookie and directories with large file names don't progress.
    // Instead, just grow the buffer until the entire directory fits.
    while (true) {
      val errno = fd_readdir(
        fd = fd,
        buf = bufPointer.address.toInt(),
        buf_len = bufSize,
        cookie = 0L, // Don't bother with dircookie, it doesn't work for large file names.
        returnPointer = returnPointer.address.toInt(),
      )

      if (errno != 0) throw ErrnoException(errno.toShort())
      pageSize = returnPointer.loadInt()

      if (pageSize < bufSize) break

      bufSize *= 4
      bufPointer = allocator.allocate(bufSize)
    }

    var pos = bufPointer
    val limit = bufPointer + pageSize
    val result = mutableListOf<DirectoryEntry>()
    while (pos.address < limit.address) {
      pos += 8 // Skip dircookie.
      val d_inod: inode = pos.loadLong()
      pos += 8
      val d_namelen: dirnamelen = pos.loadInt()
      pos += 4
      val d_type = pos.loadInt()
      pos += 4

      val name = pos.readString(d_namelen)
      pos += d_namelen

      result += DirectoryEntry(
        inode = d_inod,
        type = d_type.toByte(),
        name = name.toPath(),
      )
    }
    return result
  }
}

private fun Pointer.readString(byteCount: Int): String {
  if (byteCount == 0) return ""

  // Drop the last byte if it's \0.
  // TODO: confirm this is necessary in practice.
  val lastByte = (this + byteCount - 1).loadByte()
  val byteArray = when {
    lastByte.toInt() == 0 -> readByteArray(byteCount - 1)
    else -> readByteArray(byteCount)
  }

  return byteArray.decodeToString()
}

private fun Pointer.readByteArray(byteCount: Int): ByteArray {
  val result = ByteArray(byteCount)
  for (i in 0 until byteCount) {
    result[i] = (this + i).loadByte()
  }
  return result
}

private fun MemoryAllocator.write(path: String): Pair<Pointer, size> {
  val bytes = path.encodeToByteArray()
  return write(bytes) to bytes.size
}

private fun MemoryAllocator.write(byteArray: ByteArray): Pointer {
  val result = allocate(byteArray.size)
  var pos = result
  for (b in byteArray) {
    pos.storeByte(b)
    pos += 1
  }
  return result
}
