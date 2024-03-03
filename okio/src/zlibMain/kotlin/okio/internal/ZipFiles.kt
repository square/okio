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
package okio.internal

import okio.BufferedSource
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.ZipFileSystem
import okio.buffer
import okio.use

private const val LOCAL_FILE_HEADER_SIGNATURE = 0x4034b50
private const val CENTRAL_FILE_HEADER_SIGNATURE = 0x2014b50
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x6054b50
private const val ZIP64_LOCATOR_SIGNATURE = 0x07064b50
private const val ZIP64_EOCD_RECORD_SIGNATURE = 0x06064b50

internal const val COMPRESSION_METHOD_DEFLATED = 8
internal const val COMPRESSION_METHOD_STORED = 0

/** General Purpose Bit Flags, Bit 0. Set if the file is encrypted. */
private const val BIT_FLAG_ENCRYPTED = 1 shl 0

/**
 * General purpose bit flags that this implementation handles. Strict enforcement of additional
 * flags may break legitimate use cases.
 */
private const val BIT_FLAG_UNSUPPORTED_MASK = BIT_FLAG_ENCRYPTED

/** Max size of entries and archives without zip64. */
private const val MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE = 0xffffffffL

private const val HEADER_ID_ZIP64_EXTENDED_INFO = 0x1
private const val HEADER_ID_NTFS_EXTRA = 0x000a
private const val HEADER_ID_EXTENDED_TIMESTAMP = 0x5455

/**
 * Opens the file at [zipPath] for use as a file system. This uses UTF-8 to comments and names in
 * the zip file.
 *
 * @param predicate a function that returns false for entries that should be omitted from the file
 *     system.
 */
@Throws(IOException::class)
internal fun openZip(
  zipPath: Path,
  fileSystem: FileSystem,
  predicate: (ZipEntry) -> Boolean = { true },
): ZipFileSystem {
  fileSystem.openReadOnly(zipPath).use { fileHandle ->
    // Scan backwards from the end of the file looking for the END_OF_CENTRAL_DIRECTORY_SIGNATURE.
    // If this file has no comment we'll see it on the first attempt; otherwise we have to go
    // backwards byte-by-byte until we reach it. (The number of bytes scanned will equal the comment
    // size).
    var scanOffset = fileHandle.size() - 22 // end of central directory record size is 22 bytes.
    if (scanOffset < 0L) {
      throw IOException("not a zip: size=${fileHandle.size()}")
    }
    val stopOffset = maxOf(scanOffset - 65_536L, 0L)
    val eocdOffset: Long
    var record: EocdRecord
    val comment: String
    while (true) {
      val source = fileHandle.source(scanOffset).buffer()
      try {
        if (source.readIntLe() == END_OF_CENTRAL_DIRECTORY_SIGNATURE) {
          eocdOffset = scanOffset
          record = source.readEocdRecord()
          comment = source.readUtf8(record.commentByteCount.toLong())
          break
        }
      } finally {
        source.close()
      }

      scanOffset--
      if (scanOffset < stopOffset) {
        throw IOException("not a zip: end of central directory signature not found")
      }
    }

    // If this is a zip64, read a zip64 central directory record.
    val zip64LocatorOffset = eocdOffset - 20 // zip64 end of central directory locator is 20 bytes.
    if (zip64LocatorOffset > 0L) {
      fileHandle.source(zip64LocatorOffset).buffer().use { zip64LocatorSource ->
        if (zip64LocatorSource.readIntLe() == ZIP64_LOCATOR_SIGNATURE) {
          val diskWithCentralDir = zip64LocatorSource.readIntLe()
          val zip64EocdRecordOffset = zip64LocatorSource.readLongLe()
          val numDisks = zip64LocatorSource.readIntLe()
          if (numDisks != 1 || diskWithCentralDir != 0) {
            throw IOException("unsupported zip: spanned")
          }
          fileHandle.source(zip64EocdRecordOffset).buffer().use { zip64EocdSource ->
            val zip64EocdSignature = zip64EocdSource.readIntLe()
            if (zip64EocdSignature != ZIP64_EOCD_RECORD_SIGNATURE) {
              throw IOException(
                "bad zip: expected ${ZIP64_EOCD_RECORD_SIGNATURE.hex} " +
                  "but was ${zip64EocdSignature.hex}",
              )
            }
            record = zip64EocdSource.readZip64EocdRecord(record)
          }
        }
      }
    }

    // Seek to the first central directory entry and read all of the entries.
    val entries = mutableListOf<ZipEntry>()
    fileHandle.source(record.centralDirectoryOffset).buffer().use { source ->
      for (i in 0 until record.entryCount) {
        val entry = source.readCentralDirectoryZipEntry()
        if (entry.offset >= record.centralDirectoryOffset) {
          throw IOException("bad zip: local file header offset >= central directory offset")
        }
        if (predicate(entry)) {
          entries += entry
        }
      }
    }

    // Organize the entries into a tree.
    val index = buildIndex(entries)

    return ZipFileSystem(zipPath, fileSystem, index, comment)
  }
}

/**
 * Returns a map containing all of [entries], plus parent entries required so that all entries
 * (other than the file system root `/`) have a parent.
 */
private fun buildIndex(entries: List<ZipEntry>): Map<Path, ZipEntry> {
  val root = "/".toPath()
  val result = mutableMapOf(
    root to ZipEntry(canonicalPath = root, isDirectory = true),
  )

  // Iterate in sorted order so each path is preceded by its parent.
  for (entry in entries.sortedBy { it.canonicalPath }) {
    // Note that this may clobber an existing element in the map. For consistency with java.util.zip
    // and java.nio.file.FileSystem, this prefers the last-encountered element.
    val replaced = result.put(entry.canonicalPath, entry)
    if (replaced != null) continue

    // Make sure this parent directories exist all the way up to the file system root.
    var child = entry
    while (true) {
      val parentPath = child.canonicalPath.parent ?: break // child is '/'.
      var parentEntry = result[parentPath]

      // We've found a parent that already exists! Add the child; we're done.
      if (parentEntry != null) {
        parentEntry.children += child.canonicalPath
        break
      }

      // A parent is missing! Synthesize one.
      parentEntry = ZipEntry(
        canonicalPath = parentPath,
        isDirectory = true,
      )
      result[parentPath] = parentEntry
      parentEntry.children += child.canonicalPath
      child = parentEntry
    }
  }

  return result
}

/** When this returns, [this] will be positioned at the start of the next entry. */
@Throws(IOException::class)
internal fun BufferedSource.readCentralDirectoryZipEntry(): ZipEntry {
  val signature = readIntLe()
  if (signature != CENTRAL_FILE_HEADER_SIGNATURE) {
    throw IOException(
      "bad zip: expected ${CENTRAL_FILE_HEADER_SIGNATURE.hex} but was ${signature.hex}",
    )
  }

  skip(4) // version made by (2) + version to extract (2).
  val bitFlag = readShortLe().toInt() and 0xffff
  if (bitFlag and BIT_FLAG_UNSUPPORTED_MASK != 0) {
    throw IOException("unsupported zip: general purpose bit flag=${bitFlag.hex}")
  }

  val compressionMethod = readShortLe().toInt() and 0xffff
  val dosLastModifiedTime = readShortLe().toInt() and 0xffff
  val dosLastModifiedDate = readShortLe().toInt() and 0xffff

  // These are 32-bit values in the file, but 64-bit fields in this object.
  val crc = readIntLe().toLong() and 0xffffffffL
  var compressedSize = readIntLe().toLong() and 0xffffffffL
  var size = readIntLe().toLong() and 0xffffffffL
  val nameSize = readShortLe().toInt() and 0xffff
  val extraSize = readShortLe().toInt() and 0xffff
  val commentByteCount = readShortLe().toInt() and 0xffff

  skip(8) // disk number start (2) + internal file attributes (2) + external file attributes (4).
  var offset = readIntLe().toLong() and 0xffffffffL
  val name = readUtf8(nameSize.toLong())
  if ('\u0000' in name) throw IOException("bad zip: filename contains 0x00")

  val requiredZip64ExtraSize = run {
    var result = 0L
    if (size == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) result += 8
    if (compressedSize == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) result += 8
    if (offset == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) result += 8
    return@run result
  }

  var ntfsLastModifiedAtFiletime: Long? = null
  var ntfsLastAccessedAtFiletime: Long? = null
  var ntfsCreatedAtFiletime: Long? = null

  var hasZip64Extra = false
  readExtra(extraSize) { headerId, dataSize ->
    when (headerId) {
      HEADER_ID_ZIP64_EXTENDED_INFO -> {
        if (hasZip64Extra) {
          throw IOException("bad zip: zip64 extra repeated")
        }
        hasZip64Extra = true

        if (dataSize < requiredZip64ExtraSize) {
          throw IOException("bad zip: zip64 extra too short")
        }

        // Read each field if it has a sentinel value in the regular header.
        size = if (size == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) readLongLe() else size
        compressedSize = if (compressedSize == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) readLongLe() else 0L
        offset = if (offset == MAX_ZIP_ENTRY_AND_ARCHIVE_SIZE) readLongLe() else 0L
      }

      HEADER_ID_NTFS_EXTRA -> {
        if (dataSize < 4L) {
          throw IOException("bad zip: NTFS extra too short")
        }
        skip(4L)

        // Reads the NTFS extra metadata. This metadata recursively does a tag and length scheme
        // inside of ZIP extras' own tag and length scheme. So we do readExtra() again.
        readExtra((dataSize - 4L).toInt()) { attributeId, attributeSize ->
          when (attributeId) {
            0x1 -> {
              if (ntfsLastModifiedAtFiletime != null) {
                throw IOException("bad zip: NTFS extra attribute tag 0x0001 repeated")
              }

              if (attributeSize != 24L) {
                throw IOException("bad zip: NTFS extra attribute tag 0x0001 size != 24")
              }

              ntfsLastModifiedAtFiletime = readLongLe()
              ntfsLastAccessedAtFiletime = readLongLe()
              ntfsCreatedAtFiletime = readLongLe()
            }
          }
        }
      }
    }
  }

  if (requiredZip64ExtraSize > 0L && !hasZip64Extra) {
    throw IOException("bad zip: zip64 extra required but absent")
  }

  val comment = readUtf8(commentByteCount.toLong())
  val canonicalPath = "/".toPath() / name
  val isDirectory = name.endsWith("/")

  return ZipEntry(
    canonicalPath = canonicalPath,
    isDirectory = isDirectory,
    comment = comment,
    crc = crc,
    compressedSize = compressedSize,
    size = size,
    compressionMethod = compressionMethod,
    offset = offset,
    dosLastModifiedAtDate = dosLastModifiedDate,
    dosLastModifiedAtTime = dosLastModifiedTime,
    ntfsLastModifiedAtFiletime = ntfsLastModifiedAtFiletime,
    ntfsLastAccessedAtFiletime = ntfsLastAccessedAtFiletime,
    ntfsCreatedAtFiletime = ntfsCreatedAtFiletime,
  )
}

@Throws(IOException::class)
private fun BufferedSource.readEocdRecord(): EocdRecord {
  val diskNumber = readShortLe().toInt() and 0xffff
  val diskWithCentralDir = readShortLe().toInt() and 0xffff
  val entryCount = (readShortLe().toInt() and 0xffff).toLong()
  val totalEntryCount = (readShortLe().toInt() and 0xffff).toLong()
  if (entryCount != totalEntryCount || diskNumber != 0 || diskWithCentralDir != 0) {
    throw IOException("unsupported zip: spanned")
  }
  skip(4) // central directory size.
  val centralDirectoryOffset = readIntLe().toLong() and 0xffffffffL
  val commentByteCount = readShortLe().toInt() and 0xffff

  return EocdRecord(
    entryCount = entryCount,
    centralDirectoryOffset = centralDirectoryOffset,
    commentByteCount = commentByteCount,
  )
}

@Throws(IOException::class)
private fun BufferedSource.readZip64EocdRecord(regularRecord: EocdRecord): EocdRecord {
  skip(12) // size of central directory record (8) + version made by (2) + version to extract (2).
  val diskNumber = readIntLe()
  val diskWithCentralDirStart = readIntLe()
  val entryCount = readLongLe()
  val totalEntryCount = readLongLe()
  if (entryCount != totalEntryCount || diskNumber != 0 || diskWithCentralDirStart != 0) {
    throw IOException("unsupported zip: spanned")
  }
  skip(8) // central directory size.
  val centralDirectoryOffset = readLongLe()

  return EocdRecord(
    entryCount = entryCount,
    centralDirectoryOffset = centralDirectoryOffset,
    commentByteCount = regularRecord.commentByteCount,
  )
}

/**
 * Read a sequence of 0 or more extra fields. Each field has this structure:
 *
 *  * 2-byte header ID
 *  * 2-byte data size
 *  * variable-byte data value
 *
 * This reads each extra field and calls [block] for each. The parameters are the header ID and
 * data size. It is an error for [block] to process more bytes than the data size.
 */
private fun BufferedSource.readExtra(extraSize: Int, block: (Int, Long) -> Unit) {
  var remaining = extraSize.toLong()
  while (remaining != 0L) {
    if (remaining < 4) {
      throw IOException("bad zip: truncated header in extra field")
    }
    val headerId = readShortLe().toInt() and 0xffff
    val dataSize = readShortLe().toLong() and 0xffff
    remaining -= 4
    if (remaining < dataSize) {
      throw IOException("bad zip: truncated value in extra field")
    }
    require(dataSize)
    val sizeBefore = buffer.size
    block(headerId, dataSize)
    val fieldRemaining = dataSize + buffer.size - sizeBefore
    when {
      fieldRemaining < 0 -> {
        throw IOException("unsupported zip: too many bytes processed for $headerId")
      }
      fieldRemaining > 0 -> {
        buffer.skip(fieldRemaining)
      }
    }
    remaining -= dataSize
  }
}

internal fun BufferedSource.skipLocalHeader() {
  readOrSkipLocalHeader(null)
}

internal fun BufferedSource.readLocalHeader(centralDirectoryZipEntry: ZipEntry): ZipEntry {
  return readOrSkipLocalHeader(centralDirectoryZipEntry)!!
}

/**
 * If [centralDirectoryZipEntry] is null this will return null. Otherwise, it will return a new
 * entry which unions [centralDirectoryZipEntry] with information from the local header.
 */
private fun BufferedSource.readOrSkipLocalHeader(
  centralDirectoryZipEntry: ZipEntry?,
): ZipEntry? {
  val signature = readIntLe()
  if (signature != LOCAL_FILE_HEADER_SIGNATURE) {
    throw IOException(
      "bad zip: expected ${LOCAL_FILE_HEADER_SIGNATURE.hex} but was ${signature.hex}",
    )
  }
  skip(2) // version to extract.
  val bitFlag = readShortLe().toInt() and 0xffff
  if (bitFlag and BIT_FLAG_UNSUPPORTED_MASK != 0) {
    throw IOException("unsupported zip: general purpose bit flag=${bitFlag.hex}")
  }
  skip(18) // compression method (2) + time+date (4) + crc32 (4) + compressed size (4) + size (4).
  val fileNameLength = readShortLe().toLong() and 0xffff
  val extraSize = readShortLe().toInt() and 0xffff
  skip(fileNameLength)

  if (centralDirectoryZipEntry == null) {
    skip(extraSize.toLong())
    return null
  }

  var extendedLastModifiedAtSeconds: Int? = null
  var extendedLastAccessedAtSeconds: Int? = null
  var extendedCreatedAtSeconds: Int? = null

  readExtra(extraSize) { headerId, dataSize ->
    when (headerId) {
      HEADER_ID_EXTENDED_TIMESTAMP -> {
        if (dataSize < 1) {
          throw IOException("bad zip: extended timestamp extra too short")
        }
        val flags = readByte().toInt() and 0xff

        val hasLastModifiedAtMillis = (flags and 0x1) == 0x1
        val hasLastAccessedAtMillis = (flags and 0x2) == 0x2
        val hasCreatedAtMillis = (flags and 0x4) == 0x4
        val requiredSize = run {
          var result = 1L
          if (hasLastModifiedAtMillis) result += 4L
          if (hasLastAccessedAtMillis) result += 4L
          if (hasCreatedAtMillis) result += 4L
          return@run result
        }
        if (dataSize < requiredSize) {
          throw IOException("bad zip: extended timestamp extra too short")
        }

        if (hasLastModifiedAtMillis) extendedLastModifiedAtSeconds = readIntLe()
        if (hasLastAccessedAtMillis) extendedLastAccessedAtSeconds = readIntLe()
        if (hasCreatedAtMillis) extendedCreatedAtSeconds = readIntLe()
      }
    }
  }

  return centralDirectoryZipEntry.copy(
    extendedLastModifiedAtSeconds = extendedLastModifiedAtSeconds,
    extendedLastAccessedAtSeconds = extendedLastAccessedAtSeconds,
    extendedCreatedAtSeconds = extendedCreatedAtSeconds,
  )
}

/**
 * Converts from the Microsoft [filetime] format to the Java epoch millis format.
 *
 *  * Filetime's unit is 100 nanoseconds, and 0 is 1601-01-01T00:00:00Z.
 *  * Java epoch millis' unit is 1 millisecond, and 0 is 1970-01-01T00:00:00Z.
 *
 * See also https://learn.microsoft.com/en-us/windows/win32/api/minwinbase/ns-minwinbase-filetime
 */
internal fun filetimeToEpochMillis(filetime: Long): Long {
  // There's 11,644,473,600,000 milliseconds between 1601-01-01T00:00:00Z and 1970-01-01T00:00:00Z.
  //   val years = 1_970 − 1_601
  //   val leapYears = floor(years / 4) − floor(years / 100)
  //   val days = (years * 365) + leapYears
  //   val millis = days * 24 * 60 * 60 * 1_000
  return filetime / 10_000 - 11_644_473_600_000L
}

/**
 * Converts a 32-bit DOS date+time to milliseconds since epoch. Note that this function interprets
 * a value with no time zone as a value with the local time zone.
 */
internal fun dosDateTimeToEpochMillis(date: Int, time: Int): Long? {
  if (time == -1) {
    return null
  }

  return datePartsToEpochMillis(
    year = 1980 + (date shr 9 and 0x7f),
    month = date shr 5 and 0xf,
    day = date and 0x1f,
    hour = time shr 11 and 0x1f,
    minute = time shr 5 and 0x3f,
    second = time and 0x1f shl 1,
  )
}

private class EocdRecord(
  val entryCount: Long,
  val centralDirectoryOffset: Long,
  val commentByteCount: Int,
)

private val Int.hex: String
  get() = "0x${this.toString(16)}"
