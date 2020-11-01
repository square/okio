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
import okio.Path.Companion.toPath
import platform.posix.EACCES
import platform.posix.ENOENT
import platform.posix.ENOMEM
import platform.posix.PATH_MAX
import platform.posix.errno
import platform.posix.free
import platform.posix.getcwd

object PosixSystemFilesystem : Filesystem() {
  override fun cwd(): Path {
    val pathMax = PATH_MAX
    val bytes: CPointer<ByteVarOf<Byte>>? = getcwd(null, pathMax.toULong())
    try {
      if (bytes == null) {
        when (errno) {
          ENOENT -> throw IOException("ENOENT: the current working directory no longer exists")
          EACCES -> throw IOException("EACCES: cannot access the current working directory")
          ENOMEM -> throw OutOfMemoryError()
          else -> error("unexpected errno $errno")
        }
      }
      return Buffer().writeNullTerminated(bytes).toPath()
    } finally {
      free(bytes)
    }
  }
}
