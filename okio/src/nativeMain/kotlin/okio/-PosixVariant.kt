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
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import okio.Path.Companion.toPath
import okio.internal.toPath
import platform.posix.PATH_MAX
import platform.posix.S_IFLNK
import platform.posix.S_IFMT
import platform.posix.errno
import platform.posix.readlink
import platform.posix.stat

@ExperimentalFileSystem
internal expect val PLATFORM_TEMPORARY_DIRECTORY: Path

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantDelete(path: Path)

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantMkdir(dir: Path): Int

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantCanonicalize(path: Path): Path

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantMetadataOrNull(path: Path): FileMetadata?

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantMove(source: Path, target: Path)

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantSource(file: Path): Source

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantSink(file: Path, mustCreate: Boolean): Sink

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantAppendingSink(file: Path): Sink

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantOpenReadOnly(file: Path): FileHandle

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantOpenReadWrite(
  file: Path,
  mustCreate: Boolean,
  mustExist: Boolean
): FileHandle

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantCreateSymlink(source: Path, target: Path)

@ExperimentalFileSystem
internal fun symlinkTarget(stat: stat, path: Path): Path? {
  if (stat.st_mode.toInt() and S_IFMT != S_IFLNK) return null

  // `path` is a symlink, let's resolve its target.
  memScoped {
    val buffer = allocArray<ByteVar>(PATH_MAX)
    val byteCount = readlink(path.toString(), buffer, PATH_MAX)
    if (byteCount.toInt() == -1) {
      throw errnoToIOException(errno)
    }
    return buffer.toKString().toPath()
  }
}
