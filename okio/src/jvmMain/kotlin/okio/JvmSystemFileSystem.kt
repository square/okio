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

import java.nio.channels.FileChannel
import java.nio.file.NoSuchFileException
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import okio.Path.Companion.toOkioPath

/**
 * A file system that adapts `java.io`.
 *
 * This base class is used on Android API levels 15 (our minimum supported API) through 26
 * (the first release that includes java.nio.file).
 */
@ExperimentalFileSystem
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
      size = size,
      createdAtMillis = null,
      lastModifiedAtMillis = lastModifiedAtMillis,
      lastAccessedAtMillis = null
    )
  }

  override fun list(dir: Path): List<Path> {
    val file = dir.toFile()
    val entries = file.list()
    if (entries == null) {
      if (!file.exists()) throw FileNotFoundException("no such file: $dir")
      throw IOException("failed to list $dir")
    }
    val result = entries.mapTo(mutableListOf()) { dir / it }
    result.sort()
    return result
  }

  override fun open(
    file: Path,
    read: Boolean,
    write: Boolean
  ): FileHandle {
    val openOptions = mutableSetOf<OpenOption>()
    if (read) {
      openOptions += StandardOpenOption.READ
    }
    if (write) {
      openOptions += StandardOpenOption.WRITE
      openOptions += StandardOpenOption.CREATE
    }
    try {
      val fileChannel = FileChannel.open(file.toNioPath(), openOptions)
      return JvmFileHandle(fileChannel)
    } catch (e: NoSuchFileException) {
      throw FileNotFoundException()
        .apply { initCause(e) }
    }
  }

  override fun source(file: Path): Source {
    return file.toFile().source()
  }

  override fun sink(file: Path): Sink {
    return file.toFile().sink()
  }

  override fun appendingSink(file: Path): Sink {
    return file.toFile().sink(append = true)
  }

  override fun createDirectory(dir: Path) {
    if (!dir.toFile().mkdir()) throw IOException("failed to create directory: $dir")
  }

  override fun atomicMove(source: Path, target: Path) {
    val renamed = source.toFile().renameTo(target.toFile())
    if (!renamed) throw IOException("failed to move $source to $target")
  }

  override fun delete(path: Path) {
    val file = path.toFile()
    val deleted = file.delete()
    if (!deleted) {
      if (!file.exists()) throw FileNotFoundException("no such file: $path")
      else throw IOException("failed to delete $path")
    }
  }

  override fun toString() = "JvmSystemFileSystem"
}
