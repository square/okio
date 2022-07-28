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
package okio

import okio.Path.Companion.toPath
import okio.internal.COMPRESSION_METHOD_STORED
import okio.internal.FixedLengthSource
import okio.internal.ZipEntry
import okio.internal.readLocalHeader
import okio.internal.skipLocalHeader
import java.io.FileNotFoundException
import java.util.zip.Inflater

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
internal class ZipFileSystem internal constructor(
  private val zipPath: Path,
  private val fileSystem: FileSystem,
  private val entries: Map<Path, ZipEntry>,
  private val comment: String?
) : FileSystem() {
  override fun canonicalize(path: Path): Path {
    val canonical = canonicalizeInternal(path)
    if (canonical !in entries) {
      throw FileNotFoundException("$path")
    }
    return canonical
  }

  /** Don't throw [FileNotFoundException] if the path doesn't identify a file. */
  private fun canonicalizeInternal(path: Path): Path {
    return ROOT.resolve(path, normalize = true)
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val canonicalPath = canonicalizeInternal(path)
    val entry = entries[canonicalPath] ?: return null

    val basicMetadata = FileMetadata(
      isRegularFile = !entry.isDirectory,
      isDirectory = entry.isDirectory,
      symlinkTarget = null,
      size = if (entry.isDirectory) null else entry.size,
      createdAtMillis = null,
      lastModifiedAtMillis = entry.lastModifiedAtMillis,
      lastAccessedAtMillis = null
    )

    if (entry.offset == -1L) {
      return basicMetadata
    }

    val source = fileSystem.openReadOnly(zipPath).use { fileHandle ->
      fileHandle.source(entry.offset).buffer()
    }
    return source.readLocalHeader(basicMetadata)
  }

  override fun openReadOnly(file: Path): FileHandle {
    throw UnsupportedOperationException("not implemented yet!")
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    throw IOException("zip entries are not writable")
  }

  override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)!!

  override fun listOrNull(dir: Path): List<Path>? = list(dir, throwOnFailure = false)

  private fun list(dir: Path, throwOnFailure: Boolean): List<Path>? {
    val canonicalDir = canonicalizeInternal(dir)
    val entry = entries[canonicalDir]
      ?: if (throwOnFailure) throw IOException("not a directory: $dir") else return null
    return entry.children.toList()
  }

  @Throws(IOException::class)
  override fun source(file: Path): RawSource {
    val canonicalPath = canonicalizeInternal(file)
    val entry = entries[canonicalPath] ?: throw FileNotFoundException("no such file: $file")
    val source = fileSystem.openReadOnly(zipPath).use { fileHandle ->
      fileHandle.source(entry.offset).buffer()
    }
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

  override fun sink(file: Path, mustCreate: Boolean): RawSink {
    throw IOException("zip file systems are read-only")
  }

  override fun appendingSink(file: Path, mustExist: Boolean): RawSink {
    throw IOException("zip file systems are read-only")
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean): Unit =
    throw IOException("zip file systems are read-only")

  override fun atomicMove(source: Path, target: Path): Unit =
    throw IOException("zip file systems are read-only")

  override fun delete(path: Path, mustExist: Boolean): Unit =
    throw IOException("zip file systems are read-only")

  override fun createSymlink(source: Path, target: Path): Unit =
    throw IOException("zip file systems are read-only")

  private companion object {
    val ROOT = "/".toPath()
  }
}
