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

import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path as NioPath
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import okio.Path.Companion.toOkioPath

/**
 * Extends [JvmSystemFileSystem] for platforms that support `java.nio.file` first introduced in
 * Java 7 and Android 8.0 (API level 26).
 */
internal open class NioSystemFileSystem : JvmSystemFileSystem() {
  override fun metadataOrNull(path: Path): FileMetadata? {
    return metadataOrNull(path.toNioPath())
  }

  protected fun metadataOrNull(nioPath: NioPath): FileMetadata? {
    val attributes = try {
      Files.readAttributes(
        nioPath,
        BasicFileAttributes::class.java,
        LinkOption.NOFOLLOW_LINKS,
      )
    } catch (_: NoSuchFileException) {
      return null
    } catch (_: FileSystemException) {
      return null
    }

    val symlinkTarget: NioPath? = if (attributes.isSymbolicLink) {
      Files.readSymbolicLink(nioPath)
    } else {
      null
    }

    return FileMetadata(
      isRegularFile = attributes.isRegularFile,
      isDirectory = attributes.isDirectory,
      symlinkTarget = symlinkTarget?.toOkioPath(),
      size = attributes.size(),
      createdAtMillis = attributes.creationTime()?.zeroToNull(),
      lastModifiedAtMillis = attributes.lastModifiedTime()?.zeroToNull(),
      lastAccessedAtMillis = attributes.lastAccessTime()?.zeroToNull(),
    )
  }

  /**
   * Returns this time as an epoch millis. If this is 0L this returns null, because epoch time 0L is
   * a special value that indicates the requested time was not available.
   */
  private fun FileTime.zeroToNull(): Long? {
    return toMillis().takeIf { it != 0L }
  }

  override fun atomicMove(source: Path, target: Path) {
    try {
      Files.move(source.toNioPath(), target.toNioPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } catch (e: NoSuchFileException) {
      throw FileNotFoundException(e.message)
    } catch (e: UnsupportedOperationException) {
      throw IOException("atomic move not supported")
    }
  }

  override fun createSymlink(source: Path, target: Path) {
    Files.createSymbolicLink(source.toNioPath(), target.toNioPath())
  }

  override fun toString() = "NioSystemFileSystem"
}
