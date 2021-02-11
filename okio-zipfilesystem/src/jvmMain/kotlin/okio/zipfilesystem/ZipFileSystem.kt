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
 * Read only access to a zip file.
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
    TODO()
  }

  override fun list(dir: Path): List<Path> {
    // TODO: directories might be absent in the ZIP file.
    val canonicalDir = canonicalize(dir)
    return entries.keys.mapNotNull { if (it.parent == canonicalDir) it else null }
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
