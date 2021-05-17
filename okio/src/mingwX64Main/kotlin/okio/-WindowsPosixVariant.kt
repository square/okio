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
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import okio.Path.Companion.toPath
import platform.posix.EACCES
import platform.posix.ENOENT
import platform.posix.FILE
import platform.posix.PATH_MAX
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix._chsize_s
import platform.posix._fstat64
import platform.posix._fullpath
import platform.posix._stat64
import platform.posix.errno
import platform.posix.fileno
import platform.posix.fread
import platform.posix.free
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rmdir
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MoveFileExA
import platform.windows.ReadFile
import platform.windows.WriteFile
import platform.windows._OVERLAPPED

internal actual val PLATFORM_DIRECTORY_SEPARATOR = "\\"

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantDelete(path: Path) {
  val pathString = path.toString()

  if (remove(pathString) == 0) return

  // If remove failed with EACCES, it might be a directory. Try that.
  if (errno == EACCES) {
    if (rmdir(pathString) == 0) return
  }

  throw errnoToIOException(EACCES)
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantMkdir(dir: Path): Int {
  return mkdir(dir.toString())
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantCanonicalize(path: Path): Path {
  // Note that _fullpath() returns normally if the file doesn't exist.
  val fullpath = _fullpath(null, path.toString(), PATH_MAX)
    ?: throw errnoToIOException(errno)
  try {
    val pathString = Buffer().writeNullTerminated(fullpath).readUtf8()
    if (platform.posix.access(pathString, 0) != 0 && errno == ENOENT) {
      throw FileNotFoundException("no such file")
    }
    return pathString.toPath()
  } finally {
    free(fullpath)
  }
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantMetadataOrNull(path: Path): FileMetadata? {
  return memScoped {
    val stat = alloc<_stat64>()
    if (_stat64(path.toString(), stat.ptr) != 0) {
      if (errno == ENOENT) return null
      throw errnoToIOException(errno)
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

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantMove(source: Path, target: Path) {
  if (MoveFileExA(source.toString(), target.toString(), MOVEFILE_REPLACE_EXISTING) == 0) {
    throw lastErrorToIOException()
  }
}

internal actual fun variantFread(
  target: CPointer<ByteVarOf<Byte>>,
  byteCount: UInt,
  file: CPointer<FILE>
): UInt {
  return fread(target, 1, byteCount.toULong(), file).toUInt()
}

internal actual fun variantFwrite(
  source: CPointer<ByteVar>,
  byteCount: UInt,
  file: CPointer<FILE>
): UInt {
  return fwrite(source, 1, byteCount.toULong(), file).toUInt()
}

internal actual fun variantPread(
  file: CPointer<FILE>,
  target: CValuesRef<*>,
  byteCount: Int,
  offset: Long
): Int {
  memScoped {
    val overlapped = alloc<_OVERLAPPED>()
    overlapped.Offset = offset.toUInt()
    overlapped.OffsetHigh = (offset ushr 32).toUInt()
    val bytesRead = alloc<UIntVar>()
    if (ReadFile(file, target.getPointer(this), byteCount.toUInt(), bytesRead.ptr, overlapped.ptr) == 0) {
      throw lastErrorToIOException()
    }
    return bytesRead.value.toInt()
  }
}

internal actual fun variantPwrite(
  file: CPointer<FILE>,
  source: CValuesRef<*>,
  byteCount: Int,
  offset: Long
): Int {
  memScoped {
    val overlapped = alloc<_OVERLAPPED>()
    overlapped.Offset = offset.toUInt()
    overlapped.OffsetHigh = (offset ushr 32).toUInt()
    val bytesWritten = alloc<UIntVar>()
    if (WriteFile(file, source.getPointer(this), byteCount.toUInt(), bytesWritten.ptr, overlapped.ptr) == 0) {
      throw lastErrorToIOException()
    }
    return bytesWritten.value.toInt()
  }
}

internal actual fun variantSize(file: CPointer<FILE>): Long {
  memScoped {
    val stat = alloc<_stat64>()
    if (_fstat64(fileno(file), stat.ptr) != 0) {
      throw errnoToIOException(errno)
    }
    return stat.st_size
  }
}

internal actual fun variantResize(file: CPointer<FILE>, size: Long) {
  if (_chsize_s(fileno(file), size) != 0) {
    throw errnoToIOException(errno)
  }
}
