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
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import okio.Path.Companion.toPath
import okio.internal.toPath
import platform.posix.ENOENT
import platform.posix.PATH_MAX
import platform.posix.S_IFDIR
import platform.posix.S_IFLNK
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.errno
import platform.posix.lstat
import platform.posix.readlink
import platform.posix.stat

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantMetadataOrNull(path: Path): FileMetadata? {
  return memScoped {
    val stat = alloc<stat>()
    if (lstat(path.toString(), stat.ptr) != 0) {
      if (errno == ENOENT) return null
      throw errnoToIOException(errno)
    }

    var symlinkTarget: Path? = null
    if (stat.st_mode.toInt() and S_IFMT == S_IFLNK) {
      // `path` is a symlink, let's resolve its target.
      memScoped {
        val buffer = allocArray<ByteVar>(PATH_MAX)
        val byteCount = readlink(path.toString(), buffer, PATH_MAX)
        if (byteCount.toInt() == -1) {
          throw errnoToIOException(errno)
        }
        symlinkTarget = buffer.toKString().toPath()
      }
    }

    return@memScoped FileMetadata(
      isRegularFile = stat.st_mode.toInt() and S_IFMT == S_IFREG,
      isDirectory = stat.st_mode.toInt() and S_IFMT == S_IFDIR,
      symlinkTarget = symlinkTarget,
      size = stat.st_size,
      createdAtMillis = stat.st_ctimespec.epochMillis,
      lastModifiedAtMillis = stat.st_mtimespec.epochMillis,
      lastAccessedAtMillis = stat.st_atimespec.epochMillis
    )
  }
}
