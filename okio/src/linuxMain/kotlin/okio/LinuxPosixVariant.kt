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

import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import okio.internal.linux.AT_FDCWD
import okio.internal.linux.AT_SYMLINK_NOFOLLOW
import okio.internal.linux.STATX_BASIC_STATS
import okio.internal.linux.STATX_BTIME
import okio.internal.linux.__NR_statx
import okio.internal.linux.statx
import okio.internal.linux.statx_timestamp
import platform.posix.ENOENT
import platform.posix.ENOSYS
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.errno
import platform.posix.syscall

internal actual fun PosixFileSystem.variantMetadataOrNull(path: Path): FileMetadata? {
  return memScoped {
    val statx = alloc<statx>()
    val result = syscall(
      __NR_statx.toLong(),
      AT_FDCWD,
      path.toString(),
      AT_SYMLINK_NOFOLLOW,
      STATX_BASIC_STATS or STATX_BTIME,
      statx.ptr,
    )
    if (result != 0L) {
      if (errno == ENOSYS) return null
      if (errno == ENOENT) return null
      throw errnoToIOException(errno)
    }
    return@memScoped FileMetadata(
      isRegularFile = statx.stx_mode.toInt() and S_IFMT == S_IFREG,
      isDirectory = statx.stx_mode.toInt() and S_IFMT == S_IFDIR,
      symlinkTarget = symlinkTarget(statx.stx_mode.toInt(), path),
      size = statx.stx_size.toLong(),
      createdAtMillis = statx.stx_btime.epochMillis,
      lastModifiedAtMillis = statx.stx_mtime.epochMillis,
      lastAccessedAtMillis = statx.stx_atime.epochMillis,
    )
  }
}

@OptIn(UnsafeNumber::class)
internal val statx_timestamp.epochMillis: Long
  get() = tv_sec * 1000L + tv_nsec.toLong() / 1_000_000L
