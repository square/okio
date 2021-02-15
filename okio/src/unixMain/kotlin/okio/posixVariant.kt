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
import okio.internal.toPath
import platform.posix.FILE
import platform.posix.errno
import platform.posix.fileno
import platform.posix.free
import platform.posix.fstat
import platform.posix.mkdir
import platform.posix.realpath
import platform.posix.remove
import platform.posix.rename
import platform.posix.stat
import platform.posix.timespec

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

internal actual fun variantSize(file: CPointer<FILE>): Long {
  memScoped {
    val stat = alloc<stat>()
    if (fstat(fileno(file), stat.ptr) != 0) {
      throw errnoToIOException(errno)
    }
    return stat.st_size
  }
}

internal val timespec.epochMillis: Long
  get() = tv_sec * 1000L + tv_sec / 1_000_000L
