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

import okio.Path.Companion.toPath

/**
 * Use [Node.js APIs][node_fs] to implement the Okio file system interface.
 *
 * This class needs to make calls to some fs APIs that have multiple competing overloads. To
 * unambiguously select an overload this passes `undefined` as the target type to some functions.
 *
 * [node_fs]: https://nodejs.org/dist/latest-v14.x/docs/api/fs.html
 */
@ExperimentalFileSystem
object NodeJsFileSystem : FileSystem() {
  private var S_IFMT = 0xf000 // fs.constants.S_IFMT
  private var S_IFREG = 0x8000 // fs.constants.S_IFREG
  private var S_IFDIR = 0x4000 // fs.constants.S_IFDIR
  private var S_IFLNK = 0xa000 // fs.constants.S_IFLNK

  override fun canonicalize(path: Path): Path {
    try {
      val canonicalPath = realpathSync(path.toString())
      return canonicalPath.toPath()
    } catch (e: Throwable) {
      throw e.toIOException()
    }
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val pathString = path.toString()
    val stat = try {
      lstatSync(pathString)
    } catch (e: Throwable) {
      if (e.errorCode == "ENOENT") return null // "No such file or directory".
      throw IOException(e.message)
    }

    var symlinkTarget: Path? = null
    if ((stat.mode.toInt() and S_IFMT) == S_IFLNK) {
      try {
        symlinkTarget = readlinkSync(pathString).toPath()
      } catch (e: Throwable) {
        throw e.toIOException()
      }
    }

    return FileMetadata(
      isRegularFile = (stat.mode.toInt() and S_IFMT) == S_IFREG,
      isDirectory = (stat.mode.toInt() and S_IFMT) == S_IFDIR,
      symlinkTarget = symlinkTarget,
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
          val dirent = opendir.readSync() ?: break
          result += dir / dirent.name
        }
        result.sort()
        return result
      } finally {
        opendir.closeSync()
      }
    } catch (e: Throwable) {
      throw e.toIOException()
    }
  }

  override fun openReadOnly(file: Path): FileHandle {
    val fd = openFd(file, flags = "r")
    return NodeJsFileHandle(fd, readWrite = false)
  }

  override fun openReadWrite(file: Path): FileHandle {
    val fd = if (Path.DIRECTORY_SEPARATOR == "\\") {
      // On NodeJS on Windows there's no file system flag that does all of the following:
      //  - open a file for reading, writing, seeking, and resizing
      //  - create it doesn't exist
      //  - do not truncate it if it does exist
      // Work around this by attempting to open a file that does exist (r+), falling back to
      // creating a file that does not exist (wx+) if that throws. This is not atomic.
      // https://nodejs.org/api/fs.html#fs_file_system_flags
      try {
        openFd(file, "r+")
      } catch (e: FileNotFoundException) {
        openFd(file, "wx+")
      }
    } else {
      // On other platforms 'a+' does everything that we need.
      openFd(file, "a+")
    }
    return NodeJsFileHandle(fd, readWrite = true)
  }

  override fun source(file: Path): Source {
    val fd = openFd(file, flags = "r")
    return FileSource(fd)
  }

  override fun sink(file: Path): Sink {
    val fd = openFd(file, flags = "w")
    return FileSink(fd)
  }

  override fun appendingSink(file: Path): Sink {
    val fd = openFd(file, flags = "a")
    return FileSink(fd)
  }

  private fun openFd(file: Path, flags: String): Double {
    try {
      return openSync(file.toString(), flags = flags)
    } catch (e: Throwable) {
      throw e.toIOException()
    }
  }

  override fun createDirectory(dir: Path) {
    try {
      mkdirSync(dir.toString())
    } catch (e: Throwable) {
      throw e.toIOException()
    }
  }

  override fun atomicMove(source: Path, target: Path) {
    try {
      renameSync(source.toString(), target.toString())
    } catch (e: Throwable) {
      throw e.toIOException()
    }
  }

  /**
   * We don't know if [path] is a file or a directory, but we don't (yet) have an API to delete
   * either type. Just try each in sequence.
   *
   * TODO(jwilson): switch to fs.rmSync() when our minimum requirements are Node 14.14.0.
   */
  override fun delete(path: Path) {
    try {
      unlinkSync(path.toString())
      return
    } catch (e: Throwable) {
    }
    try {
      rmdirSync(path.toString())
    } catch (e: Throwable) {
      throw e.toIOException()
    }
  }

  override fun createSymlink(source: Path, target: Path) {
    if (source.parent == null || !exists(source.parent!!)) {
      throw IOException("parent directory does not exist: ${source.parent}")
    }

    if (exists(source)) {
      throw IOException("already exists: $source")
    }

    symlinkSync(target.toString(), source.toString())
  }

  private fun Throwable.toIOException(): IOException {
    return when (errorCode) {
      "ENOENT" -> FileNotFoundException(message)
      else -> IOException(message)
    }
  }

  override fun toString() = "NodeJsSystemFileSystem"
}
