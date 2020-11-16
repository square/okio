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

import kotlinx.cinterop.toKString
import okio.Path.Companion.toPath
import platform.posix.EACCES
import platform.posix.ENOENT
import platform.posix.PATH_MAX
import platform.posix._fullpath
import platform.posix.errno
import platform.posix.free
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.remove
import platform.posix.rmdir

internal actual val VARIANT_DIRECTORY_SEPARATOR = "\\"

internal actual fun PosixSystemFilesystem.variantTemporaryDirectory(): Path {
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
