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
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import okio.Path.Companion.toPath
import platform.posix.EACCES
import platform.posix.ENOENT
import platform.posix.FILE
import platform.posix.PATH_MAX
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix._fullpath
import platform.posix._stat64
import platform.posix.errno
import platform.posix.fopen
import platform.posix.free
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rmdir
import platform.windows.CREATE_NEW
import platform.windows.CreateFileA
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GENERIC_WRITE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.MOVEFILE_REPLACE_EXISTING
import platform.windows.MoveFileExA
import platform.windows.OPEN_ALWAYS
import platform.windows.OPEN_EXISTING

internal actual val PLATFORM_TEMPORARY_DIRECTORY: Path
  get() {
    // Windows' built-in APIs check the TEMP, TMP, and USERPROFILE environment variables in order.
    // https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-gettemppatha?redirectedfrom=MSDN
    val temp = getenv("TEMP")
    if (temp != null) return temp.toKString().toPath()

    val tmp = getenv("TMP")
    if (tmp != null) return tmp.toKString().toPath()

    val userProfile = getenv("USERPROFILE")
    if (userProfile != null) return userProfile.toKString().toPath()

    return "\\Windows\\TEMP".toPath()
  }

internal actual val PLATFORM_DIRECTORY_SEPARATOR = "\\"

internal actual fun PosixFileSystem.variantDelete(path: Path, mustExist: Boolean) {
  val pathString = path.toString()

  if (remove(pathString) == 0) return

  // If remove failed with EACCES, it might be a directory. Try that.
  if (errno == EACCES) {
    if (rmdir(pathString) == 0) return
  }
  if (errno == ENOENT) {
    if (mustExist) {
      throw FileNotFoundException("no such file: $path")
    } else {
      return
    }
  }

  throw errnoToIOException(EACCES)
}

internal actual fun PosixFileSystem.variantMkdir(dir: Path): Int {
  return mkdir(dir.toString())
}

internal actual fun PosixFileSystem.variantCanonicalize(path: Path): Path {
  // Note that _fullpath() returns normally if the file doesn't exist.
  val fullpath = _fullpath(null, path.toString(), PATH_MAX.toULong())
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
      symlinkTarget = null,
      size = stat.st_size,
      createdAtMillis = stat.st_ctime * 1000L,
      lastModifiedAtMillis = stat.st_mtime * 1000L,
      lastAccessedAtMillis = stat.st_atime * 1000L,
    )
  }
}

internal actual fun PosixFileSystem.variantMove(source: Path, target: Path) {
  if (MoveFileExA(source.toString(), target.toString(), MOVEFILE_REPLACE_EXISTING.toUInt()) == 0) {
    throw lastErrorToIOException()
  }
}

internal actual fun PosixFileSystem.variantSource(file: Path): Source {
  val openFile: CPointer<FILE> = fopen(file.toString(), "rb")
    ?: throw errnoToIOException(errno)
  return FileSource(openFile)
}

internal actual fun PosixFileSystem.variantSink(file: Path, mustCreate: Boolean): Sink {
  // We're non-atomically checking file existence because Windows errors if we use the `x` flag along with `w`.
  if (mustCreate && exists(file)) throw IOException("$file already exists.")
  val openFile: CPointer<FILE> = fopen(file.toString(), "wb")
    ?: throw errnoToIOException(errno)
  return FileSink(openFile)
}

internal actual fun PosixFileSystem.variantAppendingSink(file: Path, mustExist: Boolean): Sink {
  // There is a `r+` flag which we could have used to force existence of [file] but this flag
  // doesn't allow opening for appending, and we don't currently have a way to move the cursor to
  // the end of the file. We are then forcing existence non-atomically.
  if (mustExist && !exists(file)) throw IOException("$file doesn't exist.")
  val openFile: CPointer<FILE> = fopen(file.toString(), "ab")
    ?: throw errnoToIOException(errno)
  return FileSink(openFile)
}

internal actual fun PosixFileSystem.variantOpenReadOnly(file: Path): FileHandle {
  val openFile = CreateFileA(
    lpFileName = file.toString(),
    dwDesiredAccess = GENERIC_READ,
    dwShareMode = FILE_SHARE_WRITE.toUInt(),
    lpSecurityAttributes = null,
    dwCreationDisposition = OPEN_EXISTING.toUInt(),
    dwFlagsAndAttributes = FILE_ATTRIBUTE_NORMAL.toUInt(),
    hTemplateFile = null,
  )
  if (openFile == INVALID_HANDLE_VALUE) {
    throw lastErrorToIOException()
  }
  return WindowsFileHandle(false, openFile)
}

internal actual fun PosixFileSystem.variantOpenReadWrite(
  file: Path,
  mustCreate: Boolean,
  mustExist: Boolean,
): FileHandle {
  require(!mustCreate || !mustExist) {
    "Cannot require mustCreate and mustExist at the same time."
  }

  val creationDisposition = when {
    mustCreate -> CREATE_NEW.toUInt()
    mustExist -> OPEN_EXISTING.toUInt()
    else -> OPEN_ALWAYS.toUInt()
  }

  val openFile = CreateFileA(
    lpFileName = file.toString(),
    dwDesiredAccess = GENERIC_READ or GENERIC_WRITE.toUInt(),
    dwShareMode = FILE_SHARE_WRITE.toUInt(),
    lpSecurityAttributes = null,
    dwCreationDisposition = creationDisposition,
    dwFlagsAndAttributes = FILE_ATTRIBUTE_NORMAL.toUInt(),
    hTemplateFile = null,
  )
  if (openFile == INVALID_HANDLE_VALUE) {
    throw lastErrorToIOException()
  }
  return WindowsFileHandle(true, openFile)
}

internal actual fun PosixFileSystem.variantCreateSymlink(source: Path, target: Path) {
  throw IOException("Not supported")
}
