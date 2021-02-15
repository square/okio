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

import okio.ExperimentalFileSystem
import okio.FileMetadata
import okio.FileSystem
import okio.InflaterSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.Inflater

/**
 * Read only access to a [zip file][zip_format].
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
 * The zip format has optional extensions for UNIX and NTFS timestamps.
 *
 *  * UNIX timestamps support both last-access time and last modification time. These timestamps
 *    are stored with 1-second precision using UTC.
 *
 *  * NTFS timestamps support creation time, last access time, and last modified time. These
 *    timestamps are stored with 100-millisecond precision using UTC.
 *
 * [zip_format]: https://pkware.cachefly.net/webdocs/APPNOTE/APPNOTE_6.2.0.txt
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
    val lastModifiedAtMillis = entry.getTime()
    // TODO(jwilson): decode NTFS and UNIX extra metadata to return better timestamps.
    return FileMetadata(
      isRegularFile = !entry.isDirectory,
      isDirectory = entry.isDirectory,
      size = if (entry.isDirectory) null else entry.size,
      createdAtMillis = null,
      lastModifiedAtMillis = if (lastModifiedAtMillis != -1L) lastModifiedAtMillis else null,
      lastAccessedAtMillis = null
    )
  }

  override fun list(dir: Path): List<Path> {
    val canonicalDir = canonicalize(dir)
    val entry = entries[canonicalDir] ?: throw IOException("not a directory: $dir")
    return entry.children.toList()
  }

  @Throws(IOException::class)
  override fun source(path: Path): Source {
    // Make sure this ZipEntry is in this Zip file.  We run it through the name lookup.
    val canonicalPath = canonicalize(path)
    val entry = entries[canonicalPath] ?: throw FileNotFoundException("no such file $path")
    val source = fileSystem.source(zipPath).buffer()
    val cursor = source.cursor()!!

    // We don't know the entry data's start position. All we have is the
    // position of the entry's local header.
    // http://www.pkware.com/documents/casestudies/APPNOTE.TXT
    cursor.seek(entry.localHeaderRelOffset)

    val localMagic = source.readIntLe()
    if (localMagic.toLong() != LOCSIG) {
      throwZipException("Local File Header", localMagic)
    }
    source.skip(2)

    // At position 6 we find the General Purpose Bit Flag.
    val gpbf = source.readShortLe().toInt() and 0xffff
    if (gpbf and GPBF_UNSUPPORTED_MASK != 0) {
      throw IOException("Invalid General Purpose Bit Flag: $gpbf")
    }

    // Offset 26 has the file name length, and offset 28 has the extra field length.
    // These lengths can differ from the ones in the central header.
    source.skip(18)
    val fileNameLength = source.readShortLe().toInt() and 0xffff
    val extraFieldLength = source.readShortLe().toInt() and 0xffff
    // Skip the variable-size file name and extra field data.
    source.skip((fileNameLength + extraFieldLength).toLong())

    return when (entry.compressionMethod) {
      ZipEntry.STORED -> {
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
}
