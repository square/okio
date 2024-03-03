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

import okio.FileMetadata
import okio.Path

/**
 * This class prefers NTFS timestamps, then extended timestamps, then the base ZIP timestamps.
 */
internal class ZipEntry(
  /**
   * Absolute path of this entry. If the raw name on disk contains relative paths like `..`, they
   * are not present in this path.
   */
  val canonicalPath: Path,

  /** True if this entry is a directory. When encoded directory entries' names end with `/`. */
  val isDirectory: Boolean = false,

  /** The comment on this entry. Empty if there is no comment. */
  val comment: String = "",

  /** The CRC32 of the uncompressed data, or -1 if not set. */
  val crc: Long = -1L,

  /** The compressed size in bytes, or -1 if unknown. */
  val compressedSize: Long = -1L,

  /** The uncompressed size in bytes, or -1 if unknown. */
  val size: Long = -1L,

  /** Either [COMPRESSION_METHOD_DEFLATED] or [COMPRESSION_METHOD_STORED]. */
  val compressionMethod: Int = -1,

  val offset: Long = -1L,

  /**
   * The base ZIP format tracks the [last modified timestamp][FileMetadata.lastModifiedAtMillis]. It
   * does not track [created timestamps][FileMetadata.createdAtMillis] or [last accessed
   * timestamps][FileMetadata.lastAccessedAtMillis].
   *
   * This format has severe limitations:
   *
   *  * Timestamps are 16-bit values stored with 2-second precision. Some zip encoders (WinZip,
   *    PKZIP) round up to the nearest 2 seconds; other encoders (Java) round down.
   *
   *  * Timestamps before 1980-01-01 cannot be represented. They cannot represent dates after
   *    2107-12-31.
   *
   *  * Timestamps are stored in local time with no time zone offset. If the time zone offset
   *    changes – due to daylight savings time or the zip file being sent to another time zone –
   *    file times will be incorrect. The file time will be shifted by the difference in time zone
   *    offsets between the encoder and decoder.
   */
  val dosLastModifiedAtDate: Int = -1,
  val dosLastModifiedAtTime: Int = -1,

  /**
   * NTFS timestamps (0x000a) support creation time, last access time, and last modified time.
   * These timestamps are stored with 100-millisecond precision using UTC.
   */
  val ntfsLastModifiedAtFiletime: Long? = null,
  val ntfsLastAccessedAtFiletime: Long? = null,
  val ntfsCreatedAtFiletime: Long? = null,

  /**
   * Extended timestamps (0x5455) are stored as signed 32-bit timestamps with 1-second precision.
   * These cannot express dates beyond 2038-01-19.
   */
  val extendedLastModifiedAtSeconds: Int? = null,
  val extendedLastAccessedAtSeconds: Int? = null,
  val extendedCreatedAtSeconds: Int? = null,
) {
  val children = mutableListOf<Path>()

  internal fun copy(
    extendedLastModifiedAtSeconds: Int?,
    extendedLastAccessedAtSeconds: Int?,
    extendedCreatedAtSeconds: Int?,
  ) = ZipEntry(
    canonicalPath = canonicalPath,
    isDirectory = isDirectory,
    comment = comment,
    crc = crc,
    compressedSize = compressedSize,
    size = size,
    compressionMethod = compressionMethod,
    offset = offset,
    dosLastModifiedAtDate = dosLastModifiedAtDate,
    dosLastModifiedAtTime = dosLastModifiedAtTime,
    ntfsLastModifiedAtFiletime = ntfsLastModifiedAtFiletime,
    ntfsLastAccessedAtFiletime = ntfsLastAccessedAtFiletime,
    ntfsCreatedAtFiletime = ntfsCreatedAtFiletime,
    extendedLastModifiedAtSeconds = extendedLastModifiedAtSeconds,
    extendedLastAccessedAtSeconds = extendedLastAccessedAtSeconds,
    extendedCreatedAtSeconds = extendedCreatedAtSeconds,
  )

  internal val lastAccessedAtMillis: Long?
    get() = when {
      ntfsLastAccessedAtFiletime != null -> filetimeToEpochMillis(ntfsLastAccessedAtFiletime)
      extendedLastAccessedAtSeconds != null -> extendedLastAccessedAtSeconds * 1000L
      else -> null
    }

  internal val lastModifiedAtMillis: Long?
    get() = when {
      ntfsLastModifiedAtFiletime != null -> filetimeToEpochMillis(ntfsLastModifiedAtFiletime)
      extendedLastModifiedAtSeconds != null -> extendedLastModifiedAtSeconds * 1000L
      dosLastModifiedAtTime != -1 -> {
        dosDateTimeToEpochMillis(dosLastModifiedAtDate, dosLastModifiedAtTime)
      }
      else -> null
    }

  internal val createdAtMillis: Long?
    get() = when {
      ntfsCreatedAtFiletime != null -> filetimeToEpochMillis(ntfsCreatedAtFiletime)
      extendedCreatedAtSeconds != null -> extendedCreatedAtSeconds * 1000L
      else -> null
    }
}
