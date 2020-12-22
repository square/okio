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

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.Path.Companion.toPath
import platform.posix.EACCES
import platform.posix.ENOENT
import platform.posix.PATH_MAX
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix._fullpath
import platform.posix._stat64
import platform.posix.errno
import platform.posix.free
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rmdir
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MoveFileExA

internal actual val VARIANT_DIRECTORY_SEPARATOR = "\\"

internal actual fun PosixSystemFilesystem.variantDelete(path: Path) {
  val pathString = path.toString()

  if (remove(pathString) == 0) return

  // If remove failed with EACCES, it might be a directory. Try that.
  if (errno == EACCES) {
    if (rmdir(pathString) == 0) return
  }

  throw IOException(errnoString(EACCES))
}

internal actual fun PosixSystemFilesystem.variantMkdir(dir: Path): Int {
  return mkdir(dir.toString())
}

internal actual fun PosixSystemFilesystem.variantCanonicalize(path: Path): Path {
  // Note that _fullpath() returns normally if the file doesn't exist.
  val fullpath = _fullpath(null, path.toString(), PATH_MAX)
    ?: throw IOException(errnoString(errno))
  try {
    val pathString = Buffer().writeNullTerminated(fullpath).readUtf8()
    if (platform.posix.access(pathString, 0) != 0 && errno == ENOENT) throw IOException("no such file")
    return pathString.toPath()
  } finally {
    free(fullpath)
  }
}

internal actual fun PosixSystemFilesystem.variantMetadata(path: Path): FileMetadata {
  return memScoped {
    val stat = alloc<_stat64>()
    if (_stat64(path.toString(), stat.ptr) != 0) {
      throw IOException(errnoString(errno))
    }
    return@memScoped FileMetadata(
      isRegularFile = stat.st_mode.toInt() and S_IFMT == S_IFREG,
      isDirectory = stat.st_mode.toInt() and S_IFMT == S_IFDIR,
      size = stat.st_size,
      createdAtMillis = stat.st_ctime * 1000L,
      lastModifiedAtMillis = stat.st_mtime * 1000L,
      lastAccessedAtMillis = stat.st_atime * 1000L
    )
  }
}

internal actual fun PosixSystemFilesystem.variantMove(source: Path, target: Path) {
  if (MoveFileExA(source.toString(), target.toString(), MOVEFILE_REPLACE_EXISTING) == 0) {
    throw IOException(lastErrorString())
  }
}
