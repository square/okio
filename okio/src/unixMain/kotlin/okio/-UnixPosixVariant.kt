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
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.toKString
import okio.Path.Companion.toPath
import okio.internal.toPath
import platform.posix.FILE
import platform.posix.errno
import platform.posix.fopen
import platform.posix.free
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.realpath
import platform.posix.remove
import platform.posix.rename
import platform.posix.symlink
import platform.posix.timespec

@ExperimentalFileSystem
internal actual val PLATFORM_TEMPORARY_DIRECTORY: Path
  get() {
    val tmpdir = getenv("TMPDIR")
    if (tmpdir != null) return tmpdir.toKString().toPath()

    return "/tmp".toPath()
  }

internal actual val PLATFORM_DIRECTORY_SEPARATOR = "/"

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantDelete(path: Path) {
  val result = remove(path.toString())
  if (result != 0) {
    throw errnoToIOException(errno)
  }
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantMkdir(dir: Path): Int {
  return mkdir(dir.toString(), 0b111111111 /* octal 777 */)
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantCanonicalize(path: Path): Path {
  // Note that realpath() fails if the file doesn't exist.
  val fullpath = realpath(path.toString(), null)
    ?: throw errnoToIOException(errno)
  try {
    return Buffer().writeNullTerminated(fullpath).toPath()
  } finally {
    free(fullpath)
  }
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantMove(
  source: Path,
  target: Path
) {
  val result = rename(source.toString(), target.toString())
  if (result != 0) {
    throw errnoToIOException(errno)
  }
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantSource(file: Path): Source {
  val openFile: CPointer<FILE> = fopen(file.toString(), "r")
    ?: throw errnoToIOException(errno)
  return FileSource(openFile)
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantSink(file: Path, mustCreate: Boolean): Sink {
  val openFile: CPointer<FILE> = fopen(file.toString(), if (mustCreate) "wx" else "w")
    ?: throw errnoToIOException(errno)
  return FileSink(openFile)
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantAppendingSink(file: Path): Sink {
  val openFile: CPointer<FILE> = fopen(file.toString(), "a")
    ?: throw errnoToIOException(errno)
  return FileSink(openFile)
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantOpenReadOnly(file: Path): FileHandle {
  val openFile: CPointer<FILE> = fopen(file.toString(), "r")
    ?: throw errnoToIOException(errno)
  return UnixFileHandle(false, openFile)
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantOpenReadWrite(file: Path): FileHandle {
  val openFile: CPointer<FILE> = fopen(file.toString(), "a+")
    ?: throw errnoToIOException(errno)
  return UnixFileHandle(true, openFile)
}

@ExperimentalFileSystem
internal actual fun PosixFileSystem.variantCreateSymlink(source: Path, target: Path) {
  if (source.parent == null || !exists(source.parent!!)) {
    throw IOException("parent directory does not exist: ${source.parent}")
  }

  if (exists(source)) {
    throw IOException("already exists: $source")
  }

  val result = symlink(target.toString(), source.toString())
  if (result != 0) {
    throw errnoToIOException(errno)
  }
}

internal expect fun variantPread(
  file: CPointer<FILE>,
  target: CValuesRef<*>,
  byteCount: Int,
  offset: Long
): Int

internal expect fun variantPwrite(
  file: CPointer<FILE>,
  source: CValuesRef<*>,
  byteCount: Int,
  offset: Long
): Int

internal val timespec.epochMillis: Long
  get() = tv_sec * 1000L + tv_sec / 1_000_000L
