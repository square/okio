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
import platform.posix.ENOENT
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.errno
import platform.posix.lstat
import platform.posix.stat

internal actual fun PosixFileSystem.variantMetadataOrNull(path: Path): FileMetadata? {
  return memScoped {
    val stat = alloc<stat>()
    if (lstat(path.toString(), stat.ptr) != 0) {
      if (errno == ENOENT) return null
      throw errnoToIOException(errno)
    }
    return@memScoped FileMetadata(
      isRegularFile = stat.st_mode.toInt() and S_IFMT == S_IFREG,
      isDirectory = stat.st_mode.toInt() and S_IFMT == S_IFDIR,
      symlinkTarget = symlinkTarget(stat, path),
      size = stat.st_size,
      createdAtMillis = stat.st_ctim.epochMillis,
      lastModifiedAtMillis = stat.st_mtim.epochMillis,
      lastAccessedAtMillis = stat.st_atim.epochMillis,
    )
  }
}
