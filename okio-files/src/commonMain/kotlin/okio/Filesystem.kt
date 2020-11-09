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

abstract class Filesystem {
  /**
   * Returns the current process's working directory. This is the result of the `getcwd` command on
   * POSIX and the `user.dir` System property in Java. This is used as the base directory when
   * relative paths are used with this filesystem.
   *
   * @throws IOException if the current process doesn't have access to the current working
   *     directory, if it's been deleted since the current process started, or there is another
   *     failure accessing the current working directory.
   */
  @Throws(IOException::class)
  abstract fun baseDirectory(): Path

  /**
   * Returns the children of the directory identified by [dir].
   *
   * @throws IOException if [dir] does not exist, is not a directory, or cannot be read. A directory
   *     cannot be read if the current process doesn't have access to [dir], or if there's a loop of
   *     symbolic links, or if any name is too long.
   */
  @Throws(IOException::class)
  abstract fun list(dir: Path): List<Path>

  /**
   * Returns a source that reads the bytes of [file] from beginning to end.
   *
   * @throws IOException if [file] does not exist, is not a file, or cannot be read. A file cannot
   *     be read if the current process doesn't have access to [file], if there's a loop of symbolic
   *     links, or if any name is too long.
   */
  @Throws(IOException::class)
  abstract fun source(file: Path): Source

  /**
   * Returns a sink that writes bytes to [file] from beginning to end. If [file] already exists it
   * will be replaced with the new data.
   *
   * @throws IOException if [file] cannot be written. A file cannot be written if its enclosing
   *     directory does not exist, if the current process doesn't have access to [file], if there's
   *     a loop of symbolic links, or if any name is too long.
   */
  @Throws(IOException::class)
  abstract fun sink(file: Path): Sink

  /**
   * Creates a directory at the path identified by [dir].
   *
   * @throws IOException if [dir]'s parent does not exist, is not a directory, or cannot be written.
   *     A directory cannot be created if the current process doesn't have access, if there's a
   *     loop of symbolic links, or if any name is too long.
   */
  @Throws(IOException::class)
  abstract fun createDirectory(dir: Path)

  /**
   * Moves [source] to [target] in-place if the underlying file system supports it. If [target]
   * exists, it is first removed. If `source == target`, this operation does nothing. This may be
   * used to move a file or a directory.
   *
   * If the file cannot be moved atomically, no move is performed and this method throws an
   * [IOException]. Typically atomic moves are not possible if the paths are on different
   * logical devices (filesystems).
   *
   * @throws IOException if the move cannot be performed, or cannot be performed atomically. Moves
   *     fail if the source doesn't exist, if the target is not writable, if the target already
   *     exists and cannot be replaced, or if the move would cause physical or quota limits to be
   *     exceeded. This list of potential problems is not exhaustive.
   */
  @Throws(IOException::class)
  abstract fun atomicMove(source: Path, target: Path)

  /**
   * Copies all of the bytes from the file at [source] to the file at [target]. This does not copy
   * file metadata like last modified time, permissions, or extended attributes.
   *
   * This function is not atomic; a failure may leave [target] in an inconsistent state. For
   * example, [target] may be empty or contain only a prefix of [source].
   *
   * @throws IOException if [source] cannot be read or if [target] cannot be written.
   */
  @Throws(IOException::class)
  abstract fun copy(source: Path, target: Path)

  /**
   * Deletes the file or directory at [path].
   *
   * @throws IOException if there is nothing at [path] to delete, or if there is a file or directory
   *     but it could not be deleted. Deletes fail if the current process doesn't have access, if
   *     the filesystem is readonly, or if [path] is a non-empty directory.  This list of potential
   *     problems is not exhaustive.
   */
  @Throws(IOException::class)
  abstract fun delete(path: Path)

  abstract val separator: String

  companion object {
    /**
     * The current process's host filesystem. Use this instance directly, or dependency inject a
     * [Filesystem] to make code testable.
     */
    val SYSTEM: Filesystem = PLATFORM_FILESYSTEM
  }
}
