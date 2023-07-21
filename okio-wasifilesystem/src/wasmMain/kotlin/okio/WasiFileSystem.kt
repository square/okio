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
import okio.internal.preview1.FirstPreopenDirectoryTmp
import okio.internal.preview1.dirnamelen
import okio.internal.preview1.fd
import okio.internal.preview1.fd_readdir
import okio.internal.preview1.oflag_creat
import okio.internal.preview1.oflag_directory
import okio.internal.preview1.oflag_excl
import okio.internal.preview1.oflags
import okio.internal.preview1.path_create_directory
import okio.internal.preview1.path_open
import okio.internal.preview1.right_fd_readdir
import okio.internal.preview1.right_fd_write
import okio.internal.preview1.rights
import okio.internal.readString
import okio.internal.write

/**
 * Use [WASI] to implement the Okio file system interface.
 *
 * [WASI]: https://wasi.dev/
 */
object WasiFileSystem : FileSystem() {
  override fun canonicalize(path: Path): Path {
    TODO("Not yet implemented")
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    TODO("Not yet implemented")
  }

  override fun list(dir: Path): List<Path> {
    val fd = pathOpen(
      path = dir.toString(),
      oflags = oflag_directory,
      rightsBase = right_fd_readdir,
    )
    try {
      return list(dir, fd)
    } finally {
      fdClose(fd)
    }
  }

  override fun listOrNull(dir: Path): List<Path>? {
    // TODO: don't throw if the directory doesn't exist, etc.
    return list(dir)
  }

  private fun list(dir: Path, fd: fd): List<Path> {
    withScopedMemoryAllocator { allocator ->
      // In theory, fd_readdir uses a 'cookie' field to page through results. In practice the
      // NodeJS implementation doesn't honor the cookie and directories with large file names
      // don't progress. Instead, just grow the buffer until the entire directory fits.
      var bufSize = 2048
      var bufPointer = allocator.allocate(bufSize)
      val returnPointer = allocator.allocate(4) // `size` is u32, 4 bytes.
      var pageSize: Int
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

      // Parse dirent records from the buffer.
      var pos = bufPointer
      val limit = bufPointer + pageSize
      val result = mutableListOf<Path>()
      while (pos.address < limit.address) {
        pos += 8 // Skip dircookie.
        pos += 8 // Skip inode.
        val d_namelen: dirnamelen = pos.loadInt()
        pos += 4 // Consume d_namelen.
        pos += 4 // Skip d_type.

        val name = pos.readString(d_namelen)
        pos += d_namelen

        result += dir / name
      }

      return result
    }
  }

  override fun openReadOnly(file: Path): FileHandle {
    TODO("Not yet implemented")
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    TODO("Not yet implemented")
  }

  override fun source(file: Path): Source {
    TODO("Not yet implemented")
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    val oflags = when {
      mustCreate -> oflag_creat or oflag_excl
      else -> oflag_creat
    }

    return FileSink(
      fd = pathOpen(
        path = file.toString(),
        oflags = oflags,
        rightsBase = right_fd_write,
      ),
    )
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    TODO("Not yet implemented")
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    // TODO: honor mustCreate.
    withScopedMemoryAllocator { allocator ->
      val (pathAddress, pathSize) = allocator.write(dir.toString())

      val errno = path_create_directory(
        fd = FirstPreopenDirectoryTmp,
        path = pathAddress.address.toInt(),
        pathSize = pathSize,
      )
      if (errno != 0) throw ErrnoException(errno.toShort())
    }
  }

  override fun atomicMove(source: Path, target: Path) {
    TODO("Not yet implemented")
  }

  override fun delete(path: Path, mustExist: Boolean) {
    TODO("Not yet implemented")
  }

  override fun createSymlink(source: Path, target: Path) {
    TODO("Not yet implemented")
  }

  private fun pathOpen(
    path: String,
    oflags: oflags,
    rightsBase: rights,
  ): fd {
    withScopedMemoryAllocator { allocator ->
      val (pathAddress, pathSize) = allocator.write(path)

      val returnPointer: Pointer = allocator.allocate(4) // fd is u32.
      val errno = path_open(
        fd = FirstPreopenDirectoryTmp,
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

  override fun toString() = "okio.WasiFileSystem"
}
