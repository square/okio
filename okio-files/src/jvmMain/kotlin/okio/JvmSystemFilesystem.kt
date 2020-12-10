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

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime

object JvmSystemFilesystem : Filesystem() {
  override fun canonicalize(path: Path): Path {
    val canonicalFile = path.toFile().canonicalFile
    if (!canonicalFile.exists()) throw IOException("no such file")
    return canonicalFile.toOkioPath()
  }

  override fun metadata(path: Path): FileMetadata {
    val nioPath = path.toNioPath()

    val attributes = Files.readAttributes(
      nioPath,
      BasicFileAttributes::class.java,
      LinkOption.NOFOLLOW_LINKS
    )

    return FileMetadata(
      isRegularFile = attributes.isRegularFile,
      isDirectory = attributes.isDirectory,
      size = attributes.size(),
      createdAtMillis = attributes.creationTime()?.zeroToNull(),
      lastModifiedAtMillis = attributes.lastModifiedTime()?.zeroToNull(),
      lastAccessedAtMillis = attributes.lastAccessTime()?.zeroToNull()
    )
  }

  /**
   * Returns this time as a epoch millis. If this is 0L this returns null, because epoch time 0L is
   * a special value that indicates the requested time was not available.
   */
  private fun FileTime.zeroToNull(): Long? {
    return toMillis().takeIf { it != 0L }
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

  override fun createDirectory(dir: Path) {
    if (!dir.toFile().mkdir()) throw IOException("failed to create directory $dir")
  }

  override fun atomicMove(
    source: Path,
    target: Path
  ) {
    try {
      Files.move(source.toNioPath(), target.toNioPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } catch (e: UnsupportedOperationException) {
      throw IOException("atomic move not supported")
    }
  }

  override fun delete(path: Path) {
    val deleted = path.toFile().delete()
    if (!deleted) throw IOException("failed to delete $path")
  }
}
