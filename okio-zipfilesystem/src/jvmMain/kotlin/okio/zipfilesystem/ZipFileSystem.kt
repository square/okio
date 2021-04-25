/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio.zipfilesystem

import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.Inflater
import okio.ExperimentalFileSystem
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.InflaterSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer

/**
 * Read only access to a [zip file][zip_format] and common [extra fields][extra_fields].
 *
 * Zip Timestamps
 * --------------
 *
 * The base zip format tracks the [last modified timestamp][FileMetadata.lastModifiedAtMillis]. It
 * does not track [created timestamps][FileMetadata.createdAtMillis] or [last accessed
 * timestamps][FileMetadata.lastAccessedAtMillis]. This format has limitations:
 *
 *  * Timestamps are 16-bit values stored with 2-second precision. Some zip encoders (WinZip, PKZIP)
 *    round up to the nearest 2 seconds; other encoders (Java) round down.
 *
 *  * Timestamps before 1980-01-01 cannot be represented. They cannot represent dates after
 *    2107-12-31.
 *
 *  * Timestamps are stored in local time with no time zone offset. If the time zone offset changes
 *    – due to daylight savings time or the zip file being sent to another time zone – file times
 *    will be incorrect. The file time will be shifted by the difference in time zone offsets
 *    between the encoder and decoder.
 *
 * The zip format has optional extensions for timestamps.
 *
 *  * UNIX timestamps (0x000d) support both last-access time and last modification time. These
 *    timestamps are stored with 1-second precision using UTC.
 *
 *  * NTFS timestamps (0x000a) support creation time, last access time, and last modified time.
 *    These timestamps are stored with 100-millisecond precision using UTC.
 *
 *  * Extended timestamps (0x5455) are stored as signed 32-bit timestamps with 1-second precision.
 *    These cannot express dates beyond 2038-01-19.
 *
 * This class currently supports base timestamps and extended timestamps.
 *
 * [zip_format]: https://pkware.cachefly.net/webdocs/APPNOTE/APPNOTE_6.2.0.txt
 * [extra_fields]: https://opensource.apple.com/source/zip/zip-6/unzip/unzip/proginfo/extra.fld
 */
@ExperimentalFileSystem
class ZipFileSystem internal constructor(
  private val zipPath: Path,
  private val fileSystem: FileSystem,
  private val entries: Map<Path, ZipEntry>,
  private val comment: String?
) : FileSystem() {
  override fun canonicalize(path: Path): Path {
    return "/".toPath() / path
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val canonicalPath = canonicalize(path)
    val entry = entries[canonicalPath] ?: return null

    val basicMetadata = FileMetadata(
      isRegularFile = !entry.isDirectory,
      isDirectory = entry.isDirectory,
      size = if (entry.isDirectory) null else entry.size,
      createdAtMillis = null,
      lastModifiedAtMillis = entry.lastModifiedAtMillis,
      lastAccessedAtMillis = null
    )

    if (entry.offset == -1L) {
      return basicMetadata
    }

    val source = fileSystem.source(zipPath).buffer()
    val cursor = source.cursor()!!
    cursor.seek(entry.offset)
    return source.readLocalHeader(basicMetadata)
  }

  override fun open(
    file: Path,
    read: Boolean,
    write: Boolean
  ): FileHandle {
    throw UnsupportedOperationException("not implemented yet!")
  }

  override fun list(dir: Path): List<Path> {
    val canonicalDir = canonicalize(dir)
    val entry = entries[canonicalDir] ?: throw IOException("not a directory: $dir")
    return entry.children.toList()
  }

  @Throws(IOException::class)
  override fun source(path: Path): Source {
    val canonicalPath = canonicalize(path)
    val entry = entries[canonicalPath] ?: throw FileNotFoundException("no such file: $path")
    val source = fileSystem.source(zipPath).buffer()
    val cursor = source.cursor()!!

    cursor.seek(entry.offset)
    source.skipLocalHeader()

    return when (entry.compressionMethod) {
      COMPRESSION_METHOD_STORED -> {
        FixedLengthSource(source, entry.size, truncate = true)
      }
      else -> {
        val inflaterSource = InflaterSource(
          FixedLengthSource(source, entry.compressedSize, truncate = true),
          Inflater(true)
        )
        FixedLengthSource(inflaterSource, entry.size, truncate = false)
      }
    }
  }

  override fun sink(file: Path): Sink = throw IOException("zip file systems are read-only")

  override fun appendingSink(file: Path): Sink =
    throw IOException("zip file systems are read-only")

  override fun createDirectory(dir: Path): Unit =
    throw IOException("zip file systems are read-only")

  override fun atomicMove(source: Path, target: Path): Unit =
    throw IOException("zip file systems are read-only")

  override fun delete(path: Path): Unit = throw IOException("zip file systems are read-only")

  companion object {
    @Throws(IOException::class)
    @ExperimentalFileSystem
    @JvmStatic @JvmName("open")
    fun FileSystem.openZip(zipPath: Path): ZipFileSystem = open(zipPath, this)
  }
}
