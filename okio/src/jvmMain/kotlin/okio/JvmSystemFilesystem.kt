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

/**
 * A filesystem that adapts `java.io`.
 *
 * This base class is used on Android API levels 15 (our minimum supported API) through 26
 * (the first release that includes java.nio.file).
 */
@ExperimentalFilesystem
internal open class JvmSystemFilesystem : Filesystem() {
  override fun canonicalize(path: Path): Path {
    val canonicalFile = path.toFile().canonicalFile
    if (!canonicalFile.exists()) throw IOException("no such file")
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
    val entries = dir.toFile().list() ?: throw IOException("failed to list $dir")
    return entries.map { dir / it }
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
    if (!dir.toFile().mkdir()) throw IOException("failed to create directory $dir")
  }

  override fun atomicMove(source: Path, target: Path) {
    val renamed = source.toFile().renameTo(target.toFile())
    if (!renamed) throw IOException("failed to move $source to $target")
  }

  override fun delete(path: Path) {
    val deleted = path.toFile().delete()
    if (!deleted) throw IOException("failed to delete $path")
  }

  override fun toString() = "JvmSystemFilesystem"
}
