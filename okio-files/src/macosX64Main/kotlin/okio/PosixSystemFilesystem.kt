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

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.get
import okio.Path.Companion.toPath
import platform.posix.DIR
import platform.posix.EACCES
import platform.posix.ELOOP
import platform.posix.ENAMETOOLONG
import platform.posix.ENOENT
import platform.posix.ENOMEM
import platform.posix.ENOTDIR
import platform.posix.PATH_MAX
import platform.posix.closedir
import platform.posix.dirent
import platform.posix.errno
import platform.posix.free
import platform.posix.getcwd
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.set_posix_errno

internal object PosixSystemFilesystem : Filesystem() {
  private val SELF_DIRECTORY_ENTRY = ".".toPath()
  private val PARENT_DIRECTORY_ENTRY = "..".toPath()

  override fun cwd(): Path {
    val pathMax = PATH_MAX
    val bytes: CPointer<ByteVarOf<Byte>>? = getcwd(null, pathMax.toULong())
    try {
      if (bytes == null) {
        when (errno) {
          ENOENT -> throw IOException("ENOENT: the current working directory no longer exists")
          EACCES -> throw IOException("EACCES: cannot access the current working directory")
          ENOMEM -> throw OutOfMemoryError()
          else -> throw IOException("unexpected errno $errno")
        }
      }
      return Buffer().writeNullTerminated(bytes).toPath()
    } finally {
      free(bytes)
    }
  }

  override fun list(dir: Path): List<Path>? {
    var dirToString = dir.toString()

    // We use "" for cwd; this expects ".".
    if (dirToString.isEmpty()) dirToString = "."

    val opendir: CPointer<DIR>? = opendir(dirToString)

    if (opendir == null) {
      when (errno) {
        EACCES -> throw IOException("EACCES: cannot ls $dir")
        ELOOP -> throw IOException("ELOOP: symbolic link loop resolving $dir")
        ENAMETOOLONG -> throw IOException("ENAMETOOLONG: path name too long in $dir")
        ENOENT -> return null // A component in dir doesn't exist.
        ENOTDIR -> return null // `dir` is not a directory.
        else -> throw IOException("unexpected errno $errno")
      }
    }

    try {
      val result = mutableListOf<Path>()
      val buffer = Buffer()

      set_posix_errno(0) // If readdir() returns null it's either the end or an error.
      while (true) {
        val dirent: CPointer<dirent> = readdir(opendir) ?: break
        val childPath = buffer.write(
          bytes = dirent[0].d_name,
          byteCount = dirent[0].d_namlen.toInt()
        ).toPath()

        if (childPath == SELF_DIRECTORY_ENTRY || childPath == PARENT_DIRECTORY_ENTRY) {
          continue // exclude '.' and '..' from the results.
        }

        result += childPath
      }

      if (errno != 0) {
        throw IOException("failed to ls $opendir: $errno")
      }

      return result
    } finally {
      closedir(opendir) // Ignore errno from closedir.
    }
  }
}
