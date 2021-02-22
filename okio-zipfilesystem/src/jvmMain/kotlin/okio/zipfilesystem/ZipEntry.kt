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
import okio.Path

@ExperimentalFileSystem
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

  val lastModifiedAtMillis: Long? = null,

  val offset: Long = -1L
) {
  val children = mutableListOf<Path>()
}
