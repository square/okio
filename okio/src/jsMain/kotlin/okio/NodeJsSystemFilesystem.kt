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

import fs.Dirent
import fs.MakeDirectoryOptions
import fs.mkdirSync
import fs.openSync
import fs.opendirSync
import fs.statSync
import okio.Path.Companion.toPath

/**
 * Use [Node.js APIs][node_fs] to implement the Okio filesystem interface.
 *
 * This class needs to make calls to some fs APIs that have multiple competing overloads. To
 * unambiguously select an overload this passes `undefined` as the target type to some functions.
 *
 * [node_fs]: https://nodejs.org/dist/latest-v14.x/docs/api/fs.html
 */
@ExperimentalFilesystem
internal object NodeJsSystemFilesystem : Filesystem() {
  private var S_IFMT = 0xf000 // fs.constants.S_IFMT
  private var S_IFREG = 0x8000 // fs.constants.S_IFREG
  private var S_IFDIR = 0x4000 // fs.constants.S_IFDIR

  override fun canonicalize(path: Path): Path {
    try {
      val canonicalPath = fs.realpathSync(path.toString(), options = undefined as String?)
      return canonicalPath.toString().toPath()
    } catch (e: Throwable) {
      throw IOException(e.message)
    }
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val stat = try {
      statSync(path.toString())
    } catch (e: Throwable) {
      if (e.errorCode == "ENOENT") return null // "No such file or directory".
      throw IOException(e.message)
    }
    return FileMetadata(
      isRegularFile = stat.mode.toInt() and S_IFMT == S_IFREG,
      isDirectory = stat.mode.toInt() and S_IFMT == S_IFDIR,
      size = stat.size.toLong(),
      createdAtMillis = stat.ctimeMs.toLong(),
      lastModifiedAtMillis = stat.mtimeMs.toLong(),
      lastAccessedAtMillis = stat.atimeMs.toLong()
    )
  }

  /**
   * Returns the error code on this `SystemError`. This uses `asDynamic()` because our JS bindings
   * don't (yet) include the `SystemError` type.
   *
   * https://nodejs.org/dist/latest-v14.x/docs/api/errors.html#errors_class_systemerror
   * https://nodejs.org/dist/latest-v14.x/docs/api/errors.html#errors_common_system_errors
   */
  private val Throwable.errorCode
    get() = asDynamic().code

  override fun list(dir: Path): List<Path> {
    try {
      val opendir = opendirSync(dir.toString())
      try {
        val result = mutableListOf<Path>()
        while (true) {
          // Note that the signature of readSync() returns a non-nullable Dirent; that's incorrect.
          val dirent = (opendir.readSync() as Dirent?) ?: break
          result += dir / dirent.name
        }
        return result
      } finally {
        opendir.closeSync()
      }
    } catch (e: Throwable) {
      throw IOException(e.message)
    }
  }

  override fun source(file: Path): Source {
    try {
      val fd = openSync(file.toString(), flags = "r", mode = undefined as String?)
      return FileSource(fd)
    } catch (e: Throwable) {
      throw IOException(e.message)
    }
  }

  override fun sink(file: Path): Sink {
    try {
      val fd = openSync(file.toString(), flags = "w", mode = undefined as String?)
      return FileSink(fd)
    } catch (e: Throwable) {
      throw IOException(e.message)
    }
  }

  override fun appendingSink(file: Path): Sink {
    try {
      val fd = openSync(file.toString(), flags = "a", mode = undefined as String?)
      return FileSink(fd)
    } catch (e: Throwable) {
      throw IOException(e.message)
    }
  }

  override fun createDirectory(dir: Path) {
    try {
      mkdirSync(dir.toString(), options = undefined as MakeDirectoryOptions?)
    } catch (e: Throwable) {
      throw IOException(e.message)
    }
  }

  override fun atomicMove(source: Path, target: Path) {
    try {
      fs.renameSync(source.toString(), target.toString())
    } catch (e: Throwable) {
      throw IOException(e.message)
    }
  }

  /**
   * We don't know if [path] is a file or a directory, but we don't (yet) have an API to delete
   * either type. Just try each in sequence.
   *
   * TODO(jwilson): when Kotlin/JS uses a newer Node version, switch to fs.rmSync().
   */
  override fun delete(path: Path) {
    try {
      fs.unlinkSync(path.toString())
      return
    } catch (e: Throwable) {
    }
    try {
      fs.rmdirSync(path.toString())
    } catch (e: Throwable) {
      throw IOException(e.message)
    }
  }

  override fun toString() = "NodeJsSystemFilesystem"
}
