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

import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * Description of a file or another object referenced by a path.
 *
 * In simple use a file system is a mechanism for organizing files and directories on a local
 * storage device. In practice file systems are more capable and their contents more varied. For
 * example, a path may refer to:
 *
 *  * An operating system process that consumes data, produces data, or both. For example, reading
 *    from the `/dev/urandom` file on Linux returns a unique sequence of pseudorandom bytes to each
 *    reader.
 *
 *  * A stream that connects a pair of programs together. A pipe is a special file that a producing
 *    program writes to and a consuming program reads from. Both programs operate concurrently. The
 *    size of a pipe is not well defined: the writer can write as much data as the reader is able to
 *    read.
 *
 *  * A file on a remote file system. The performance and availability of remote files may be quite
 *    different from that of local files!
 *
 *  * A symbolic link (symlink) to another path. When attempting to access this path the file system
 *    will follow the link and return data from the target path.
 *
 *  * The same content as another path without a symlink. On UNIX file systems an inode is an
 *    anonymous handle to a file's content, and multiple paths may target the same inode without any
 *    other relationship to one another. A consequence of this design is that a directory with three
 *    1 GiB files may only need 1 GiB on the storage device.
 *
 * This class does not attempt to model these rich file system features! It exposes a limited view
 * useful for programs with only basic file system needs. Be cautious of the potential consequences
 * of special files when writing programs that operate on a file system.
 *
 * File metadata is subject to change, and code that operates on file systems should defend against
 * changes to the file that occur between reading metadata and subsequent operations.
 */
class FileMetadata(
  /** True if this file is a container of bytes. If this is true, then [size] is non-null. */
  val isRegularFile: Boolean = false,

  /**
   * True if the path refers to a directory that contains 0 or more child paths.
   *
   * Note that a path does not need to be a directory for [FileSystem.list] to return successfully.
   * For example, mounted storage devices may have child files, but do not identify themselves as
   * directories.
   */
  val isDirectory: Boolean = false,

  /**
   * The absolute or relative path that this file is a symlink to, or null if this is not a symlink.
   * If this is a relative path, it is relative to the source file's parent directory.
   */
  val symlinkTarget: Path? = null,

  /**
   * The number of bytes readable from this file. The amount of storage resources consumed by this
   * file may be larger (due to block size overhead, redundant copies for RAID, etc.), or smaller
   * (due to file system compression, shared inodes, etc).
   */
  val size: Long? = null,

  /**
   * The system time of the host computer when this file was created, if the host file system
   * supports this feature. This is typically available on Windows NTFS file systems and not
   * available on UNIX or Windows FAT file systems.
   */
  val createdAtMillis: Long? = null,

  /**
   * The system time of the host computer when this file was most recently written.
   *
   * Note that the accuracy of the returned time may be much more coarse than its precision. In
   * particular, this value is expressed with millisecond precision but may be accessed at
   * second- or day-accuracy only.
   */
  val lastModifiedAtMillis: Long? = null,

  /**
   * The system time of the host computer when this file was most recently read or written.
   *
   * Note that the accuracy of the returned time may be much more coarse than its precision. In
   * particular, this value is expressed with millisecond precision but may be accessed at
   * second- or day-accuracy only.
   */
  val lastAccessedAtMillis: Long? = null,

  extras: Map<KClass<*>, Any> = mapOf(),
) {
  /**
   * Additional file system-specific metadata organized by the class of that metadata. File systems
   * may use this to include information like permissions, content-type, or linked applications.
   *
   * Values in this map should be instances of immutable classes. Keys should be the types of those
   * classes.
   */
  val extras: Map<KClass<*>, Any> = extras.toMap()

  /** Returns extra metadata of type [type], or null if no such metadata is held. */
  fun <T : Any> extra(type: KClass<out T>): T? {
    val value = extras[type] ?: return null
    return type.cast(value)
  }

  fun copy(
    isRegularFile: Boolean = this.isRegularFile,
    isDirectory: Boolean = this.isDirectory,
    symlinkTarget: Path? = this.symlinkTarget,
    size: Long? = this.size,
    createdAtMillis: Long? = this.createdAtMillis,
    lastModifiedAtMillis: Long? = this.lastModifiedAtMillis,
    lastAccessedAtMillis: Long? = this.lastAccessedAtMillis,
    extras: Map<KClass<*>, Any> = this.extras,
  ): FileMetadata {
    return FileMetadata(
      isRegularFile = isRegularFile,
      isDirectory = isDirectory,
      symlinkTarget = symlinkTarget,
      size = size,
      createdAtMillis = createdAtMillis,
      lastAccessedAtMillis = lastAccessedAtMillis,
      lastModifiedAtMillis = lastModifiedAtMillis,
      extras = extras,
    )
  }

  override fun toString(): String {
    val fields = mutableListOf<String>()
    if (isRegularFile) fields += "isRegularFile"
    if (isDirectory) fields += "isDirectory"
    if (size != null) fields += "byteCount=$size"
    if (createdAtMillis != null) fields += "createdAt=$createdAtMillis"
    if (lastModifiedAtMillis != null) fields += "lastModifiedAt=$lastModifiedAtMillis"
    if (lastAccessedAtMillis != null) fields += "lastAccessedAt=$lastAccessedAtMillis"
    if (extras.isNotEmpty()) fields += "extras=$extras"
    return fields.joinToString(separator = ", ", prefix = "FileMetadata(", postfix = ")")
  }
}
