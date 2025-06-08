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

import java.io.InterruptedIOException
import java.io.RandomAccessFile
import okio.Path.Companion.toOkioPath

/**
 * A file system that adapts `java.io`.
 *
 * This base class is used on Android API levels 15 (our minimum supported API) through 26
 * (the first release that includes java.nio.file).
 */
internal open class JvmSystemFileSystem : FileSystem() {
  override fun canonicalize(path: Path): Path {
    val canonicalFile = path.toFile().canonicalFile
    if (!canonicalFile.exists()) throw FileNotFoundException("no such file")
    return canonicalFile.toOkioPath()
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val file = path.toFile()
    val isRegularFile = file.isFile
    val isDirectory = file.isDirectory
    val lastModifiedAtMillis = file.lastModified()
    val size = file.length()

    if (!isRegularFile &&
      !isDirectory &&
      lastModifiedAtMillis == 0L &&
      size == 0L &&
      !file.exists()
    ) {
      return null
    }

    return FileMetadata(
      isRegularFile = isRegularFile,
      isDirectory = isDirectory,
      symlinkTarget = null,
      size = size,
      createdAtMillis = null,
      lastModifiedAtMillis = lastModifiedAtMillis,
      lastAccessedAtMillis = null,
    )
  }

  override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)!!

  override fun listOrNull(dir: Path): List<Path>? = list(dir, throwOnFailure = false)

  private fun list(dir: Path, throwOnFailure: Boolean): List<Path>? {
    val file = dir.toFile()
    val entries = file.list()
    if (entries == null) {
      if (throwOnFailure) {
        if (!file.exists()) throw FileNotFoundException("no such file: $dir")
        throw IOException("failed to list $dir")
      } else {
        return null
      }
    }
    val result = entries.mapTo(mutableListOf()) { dir / it }
    result.sort()
    return result
  }

  override fun openReadOnly(file: Path): FileHandle {
    return JvmFileHandle(readWrite = false, randomAccessFile = RandomAccessFile(file.toFile(), "r"))
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    require(!mustCreate || !mustExist) {
      "Cannot require mustCreate and mustExist at the same time."
    }
    if (mustCreate) file.requireCreate()
    if (mustExist) file.requireExist()
    return JvmFileHandle(readWrite = true, randomAccessFile = RandomAccessFile(file.toFile(), "rw"))
  }

  override fun source(file: Path): Source {
    return file.toFile().source()
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    if (mustCreate) file.requireCreate()
    return file.toFile().sink()
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    if (mustExist) file.requireExist()
    return file.toFile().sink(append = true)
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    if (!dir.toFile().mkdir()) {
      val alreadyExist = metadataOrNull(dir)?.isDirectory == true
      if (alreadyExist) {
        if (mustCreate) {
          throw IOException("$dir already exists.")
        } else {
          return
        }
      }
      throw IOException("failed to create directory: $dir")
    }
  }

  override fun atomicMove(source: Path, target: Path) {
    // Note that on Windows, this will fail if [target] already exists.
    val renamed = source.toFile().renameTo(target.toFile())
    if (!renamed) throw IOException("failed to move $source to $target")
  }

  override fun delete(path: Path, mustExist: Boolean) {
    if (Thread.interrupted()) {
      // If the current thread has been interrupted.
      throw InterruptedIOException("interrupted")
    }
    val file = path.toFile()
    val deleted = file.delete()
    if (!deleted) {
      if (file.exists()) throw IOException("failed to delete $path")
      if (mustExist) throw FileNotFoundException("no such file: $path")
    }
  }

  override fun createSymlink(source: Path, target: Path) {
    throw IOException("unsupported")
  }

  override fun toString() = "JvmSystemFileSystem"

  // We have to implement existence verification non-atomically on the JVM because there's no API
  // to do so.
  private fun Path.requireExist() {
    if (!exists(this)) throw IOException("$this doesn't exist.")
  }

  private fun Path.requireCreate() {
    if (exists(this)) throw IOException("$this already exists.")
  }
}
