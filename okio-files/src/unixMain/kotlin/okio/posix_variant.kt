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
import platform.posix.errno
import platform.posix.free
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.realpath
import platform.posix.remove

internal actual val VARIANT_DIRECTORY_SEPARATOR = "/"

internal actual fun PosixSystemFilesystem.variantTemporaryDirectory(): Path {
  val tmpdir = getenv("TMPDIR")
  if (tmpdir != null) return tmpdir.toKString().toPath()

  return "/tmp".toPath()
}

internal actual fun PosixSystemFilesystem.variantDelete(path: Path) {
  val result = remove(path.toString())
  if (result != 0) {
    throw IOException(errnoString(errno))
  }
}

internal actual fun PosixSystemFilesystem.variantMkdir(dir: Path): Int {
  return mkdir(dir.toString(), 0b111111111 /* octal 777 */)
}

internal actual fun PosixSystemFilesystem.variantCanonicalize(path: Path): Path {
  // Note that realpath() fails if the file doesn't exist.
  val fullpath = realpath(path.toString(), null)
    ?: throw IOException(errnoString(errno))
  try {
    return Buffer().writeNullTerminated(fullpath).toPath()
  } finally {
    free(fullpath)
  }
}
