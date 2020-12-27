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
import kotlinx.cinterop.get
import okio.Path.Companion.toPath
import platform.posix.DIR
import platform.posix.FILE
import platform.posix.closedir
import platform.posix.dirent
import platform.posix.errno
import platform.posix.fopen
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.set_posix_errno

@ExperimentalFilesystem
internal object PosixSystemFilesystem : Filesystem() {
  private val SELF_DIRECTORY_ENTRY = ".".toPath()
  private val PARENT_DIRECTORY_ENTRY = "..".toPath()

  override fun canonicalize(path: Path) = variantCanonicalize(path)

  override fun metadataOrNull(path: Path): FileMetadata? {
    return variantMetadataOrNull(path)
  }

  override fun list(dir: Path): List<Path> {
    val opendir: CPointer<DIR> = opendir(dir.toString())
      ?: throw IOException(errnoString(errno))

    try {
      val result = mutableListOf<Path>()
      val buffer = Buffer()

      set_posix_errno(0) // If readdir() returns null it's either the end or an error.
      while (true) {
        val dirent: CPointer<dirent> = readdir(opendir) ?: break
        val childPath = buffer.writeNullTerminated(
          bytes = dirent[0].d_name
        ).toPath()

        if (childPath == SELF_DIRECTORY_ENTRY || childPath == PARENT_DIRECTORY_ENTRY) {
          continue // exclude '.' and '..' from the results.
        }

        result += dir / childPath
      }

      if (errno != 0) throw IOException(errnoString(errno))

      return result
    } finally {
      closedir(opendir) // Ignore errno from closedir.
    }
  }

  override fun source(file: Path): Source {
    val openFile: CPointer<FILE> = fopen(file.toString(), "r")
      ?: throw IOException(errnoString(errno))
    return FileSource(openFile)
  }

  override fun sink(file: Path): Sink {
    val openFile: CPointer<FILE> = fopen(file.toString(), "w")
      ?: throw IOException(errnoString(errno))
    return FileSink(openFile)
  }

  override fun appendingSink(file: Path): Sink {
    val openFile: CPointer<FILE> = fopen(file.toString(), "a")
      ?: throw IOException(errnoString(errno))
    return FileSink(openFile)
  }

  override fun createDirectory(dir: Path) {
    val result = variantMkdir(dir)
    if (result != 0) {
      throw IOException(errnoString(errno))
    }
  }

  override fun atomicMove(
    source: Path,
    target: Path
  ) {
    variantMove(source, target)
  }

  override fun delete(path: Path) {
    variantDelete(path)
  }

  override fun toString() = "PosixSystemFilesystem"
}
