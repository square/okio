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

/**
 * Description of a file or another object referenced by a path.
 *
 * In simple use a filesystem is a mechanism for organizing files and directories on a local storage
 * device. In practice filesystems are more capable and their contents more varied. For example, a
 * path may refer to:
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
 *  * A file on a remote filesystem. The performance and availability of remote files may be quite
 *    different from that of local files!
 *
 *  * A symbolic link (symlink) to another path. When attempting to access this path the filesystem
 *    will follow the link and return data from the target path.
 *
 *  * The same content as another path without a symlink. On UNIX filesystems an inode is an
 *    anonymous handle to a file's content, and multiple paths may target the same inode without any
 *    other relationship to one another. A consequence of this design is that a directory with three
 *    1 GiB files may only need 1 GiB on the storage device.
 *
 * This class does not attempt to model these rich filesystem features! It exposes a limited view
 * useful for programs with only basic filesystem needs. Be cautious of the potential consequences
 * of special files when writing programs that operate on a filesystem.
 *
 * File metadata is subject to change, and code that operates on filesystems should defend against
 * changes to the file that occur between reading metadata and subsequent operations.
 */
class FileMetadata(
  /** True if this file is a container of bytes. If this is true, then [size] is non-null. */
  val isRegularFile: Boolean,

  /** True if the path refers to a directory that contains 0 or more child paths. */
  val isDirectory: Boolean,

  /**
   * Returns the number of bytes readable from this file. The amount of storage resources consumed
   * by this file may be larger (due to block size overhead, redundant copies for RAID, etc.), or
   * smaller (due to filesystem compression, shared inodes, etc).
   */
  val size: Long?,

  /**
   * Returns the system time of the host computer when this file was created, if the host filesystem
   * supports this feature. This is typically available on Windows NTFS filesystems and not
   * available on UNIX or Windows FAT filesystems.
   */
  val createdAtMillis: Long?,

  /**
   * Returns the system time of the host computer when this file was most recently written.
   *
   * Note that the accuracy of the returned time may be much more coarse than its precision. In
   * particular, this value is expressed with millisecond precision but may be accessed at
   * second- or day-accuracy only.
   */
  val lastModifiedAtMillis: Long?,

  /**
   * Returns the system time of the host computer when this file was most recently read or written.
   *
   * Note that the accuracy of the returned time may be much more coarse than its precision. In
   * particular, this value is expressed with millisecond precision but may be accessed at
   * second- or day-accuracy only.
   */
  val lastAccessedAtMillis: Long?
) {
  override fun equals(other: Any?) = other is FileMetadata && toString() == other.toString()

  override fun hashCode() = toString().hashCode()

  override fun toString(): String {
    val fields = mutableListOf<String>()
    if (isRegularFile) fields += "isRegularFile"
    if (isDirectory) fields += "isDirectory"
    if (size != null) fields += "byteCount=$size"
    if (createdAtMillis != null) fields += "createdAt=$createdAtMillis"
    if (lastModifiedAtMillis != null) fields += "lastModifiedAt=$lastModifiedAtMillis"
    if (lastAccessedAtMillis != null) fields += "lastAccessedAt=$lastAccessedAtMillis"
    return fields.joinToString(separator = ", ", prefix = "FileMetadata(", postfix = ")")
  }
}
