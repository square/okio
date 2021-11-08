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

import okio.Path.Companion.toPath

/**
 * A hierarchical address on a file system. A path is an identifier only; a [FileSystem] is required
 * to access the file that a path refers to, if any.
 *
 * UNIX and Windows Paths
 * ----------------------
 *
 * Paths follow different rules on UNIX vs. Windows operating systems. On UNIX operating systems
 * (including Linux, Android, macOS, and iOS), the `/` slash character separates path segments. On
 * Windows, the `\` backslash character separates path segments. The two platforms each have their
 * own rules for path resolution. This class implements all rules on all platforms; for example you
 * can model a Linux path in a native Windows application.
 *
 * Absolute and Relative Paths
 * ---------------------------
 *
 * * **Absolute paths** identify a location independent of any working directory. On UNIX, absolute
 *   paths are prefixed with a slash, `/`. On Windows, absolute paths are one of two forms. The
 *   first is a volume letter, a colon, and a backslash, like `C:\`. The second is called a
 *   Universal Naming Convention (UNC) path, and it is prefixed by two backslashes `\\`. The term
 *   ‘fully-qualified path’ is a synonym of ‘absolute path’.
 *
 * * **Relative paths** are everything else. On their own, relative paths do not identify a
 *   location on a file system; they are relative to the system's current working directory. Use
 *   [FileSystem.canonicalize] to convert a relative path to its absolute path on a particular
 *   file system.
 *
 * There are some special cases when working with relative paths.
 *
 * On Windows, each volume (like `A:\` and `C:\`) has its own current working directory. A path
 * prefixed with a volume letter and colon but no slash (like `A:letter.doc`) is relative to the
 * working directory on the named volume. For example, if the working directory on `A:\` is
 * `A:\jesse`, then the path `A:letter.doc` resolves to `A:\jesse\letter.doc`.
 *
 * The path string `C:\Windows` is an absolute path when following Windows rules and a relative
 * path when following UNIX rules. For example, if the current working directory is
 * `/Users/jesse`, then `C:\Windows` resolves to `/Users/jesse/C:/Windows`.
 *
 * This class decides which rules to follow by inspecting the first slash character in the path
 * string. If the path contains no slash characters, it uses the host platform's rules. Or you may
 * explicitly specify which rules to use by specifying the `directorySeparator` parameter in
 * [toPath]. Pass `"/"` to get UNIX rules and `"\"` to get Windows rules.
 *
 * Path Traversal
 * --------------
 *
 * After the optional path root (like `/` on UNIX, like `X:\` or `\\` on Windows), the remainder of
 * the path is a sequence of segments separated by `/` or `\` characters. Segments satisfy these
 * rules:
 *
 *  * Segments are always non-empty.
 *  * If the segment is `.`, then the full path must be `.`.
 *  * For normalized paths, if the segment is `..`, then the path must be relative. All `..`
 *    segments precede all other segments. In all cases, a segment `..` cannot be the first segment
 *    of an absolute path.
 *
 * The only path that ends with `/` is the file system root, `/`. The dot path `.` is a relative
 * path that resolves to whichever path it is resolved against.
 *
 * The [name] is the last segment in a path. It is typically a file or directory name, like
 * `README.md` or `Desktop`. The name may be another special value:
 *
 *  * The empty string is the name of the file system root path (full path `/`).
 *  * `.` is the name of the identity relative path (full path `.`).
 *  * `..` is the name of a path consisting of only `..` segments (such as `../../..`).
 *
 * Comparing Paths
 * ---------------
 *
 * Path implements [Comparable], [equals], and [hashCode]. If two paths are equal then they operate
 * on the same file on the file system.
 *
 * Note that the converse is not true: **if two paths are non-equal, they may still resolve to the
 * same file on the file system.** Here are some of the ways non-equal paths resolve to the same
 * file:
 *
 *  * **Case differences.** The default file system on macOS is case-insensitive. The paths
 *    `/Users/jesse/notes.txt` and `/USERS/JESSE/NOTES.TXT` are non-equal but these paths resolve to
 *    the same file.
 *  * **Mounting differences.** Volumes may be mounted at multiple paths. On macOS,
 *    `/Users/jesse/notes.txt`  and `/Volumes/Macintosh HD/Users/jesse/notes.txt` typically resolve
 *    to the same file. On Windows, `C:\project\notes.txt` and `\\localhost\c$\project\notes.txt`
 *    typically resolve to the same file.
 *  * **Hard links.** UNIX file systems permit multiple paths to refer for same file. The paths may
 *    be wildly different, like `/Users/jesse/bruce_wayne.vcard` and
 *    `/Users/jesse/batman.vcard`, but changes via either path are reflected in both.
 *  * **Symlinks.** Symlinks permit multiple paths and directories to refer to the same file. On
 *     macOS `/tmp` is symlinked to `/private/tmp`, so `/tmp/notes.txt` and `/private/tmp/notes.txt`
 *     resolve to the same file.
 *
 * To test whether two paths refer to the same file, try [FileSystem.canonicalize] first. This
 * follows symlinks and looks up the preserved casing for case-insensitive case-preserved paths.
 * **This method does not guarantee a unique result, however.** For example, each hard link to a
 * file may return its own canonical path.
 *
 * Paths are sorted in case-sensitive order.
 *
 * Sample Paths
 * ------------
 *
 * <table>
 * <tr><th> Path                         <th> Parent             <th> Root       <th> Name          <th> Notes                          </tr>
 * <tr><td> `/`                          <td> null               <td> `/`        <td> (empty)       <td> root                           </tr>
 * <tr><td> `/home/jesse/notes.txt`      <td> `/home/jesse`      <td> `/`        <td> `notes.txt`   <td> absolute path                  </tr>
 * <tr><td> `project/notes.txt`          <td> `project`          <td> null       <td> `notes.txt`   <td> relative path                  </tr>
 * <tr><td> `../../project/notes.txt`    <td> `../../project`    <td> null       <td> `notes.txt`   <td> relative path with traversal   </tr>
 * <tr><td> `../../..`                   <td> null               <td> null       <td> `..`          <td> relative path with traversal   </tr>
 * <tr><td> `.`                          <td> null               <td> null       <td> `.`           <td> current working directory      </tr>
 * <tr><td> `C:\`                        <td> null               <td> `C:\`      <td> (empty)       <td> volume root (Windows)          </tr>
 * <tr><td> `C:\Windows\notepad.exe`     <td> `C:\Windows`       <td> `C:\`      <td> `notepad.exe` <td> volume absolute path (Windows) </tr>
 * <tr><td> `\`                          <td> null               <td> `\`        <td> (empty)       <td> absolute path (Windows)        </tr>
 * <tr><td> `\Windows\notepad.exe`       <td> `\Windows`         <td> `\`        <td> `notepad.exe` <td> absolute path (Windows)        </tr>
 * <tr><td> `C:`                         <td> null               <td> null       <td> (empty)       <td> volume-relative path (Windows) </tr>
 * <tr><td> `C:project\notes.txt`        <td> `C:project`        <td> null       <td> `notes.txt`   <td> volume-relative path (Windows) </tr>
 * <tr><td> `\\server`                   <td> null               <td> `\\server` <td> `server`      <td> UNC server (Windows)           </tr>
 * <tr><td> `\\server\project\notes.txt` <td> `\\server\project` <td> `\\server` <td> `notes.txt`   <td> UNC absolute path (Windows)    </tr>
 * </table>
 */
expect class Path internal constructor(bytes: ByteString) : Comparable<Path> {
  /**
   * This is the root path if this is an absolute path, or null if it is a relative path. UNIX paths
   * have a single root, `/`. Each volume on Windows is its own root, like `C:\` and `D:\`. The
   * path to the current volume `\` is its own root. Windows UNC paths like `\\server` are also
   * roots.
   */
  val root: Path?

  /**
   * The components of this path that are usually delimited by slashes. If the root is not null it
   * precedes these segments. If this path is a root its segments list is empty.
   */
  val segments: List<String>

  val segmentsBytes: List<ByteString>

  internal val bytes: ByteString

  /** This is true if [root] is not null. */
  val isAbsolute: Boolean

  /** This is true if [root] is null. */
  val isRelative: Boolean

  /**
   * This is the volume letter like "C" on Windows paths that starts with a volume letter. For
   * example, on the path "C:\Windows" this returns "C". This property is null if this is not a
   * Windows path, or if it doesn't have a volume letter.
   *
   * Note that paths that start with a volume letter are not necessarily absolute paths. For
   * example, the path "C:notepad.exe" is relative to whatever the current working directory is on
   * the C: drive.
   */
  val volumeLetter: Char?

  val nameBytes: ByteString

  val name: String

  /**
   * Returns the path immediately enclosing this path.
   *
   * This returns null if this has no parent. That includes these paths:
   *
   *  * The file system root (`/`)
   *  * The identity relative path (`.`)
   *  * A Windows volume root (like `C:\`)
   *  * A Windows Universal Naming Convention (UNC) root path (`\\server`)
   *  * A reference to the current working directory on a Windows volume (`C:`).
   *  * A series of relative paths (like `..` and `../..`).
   */
  val parent: Path?

  /** Returns true if `this == this.root`. That is, this is an absolute path with no parent. */
  val isRoot: Boolean

  /**
   * Returns a path that resolves [child] relative to this path. Note that the result isn't
   * guaranteed to be normalized even if this and [child] are both normalized themselves.
   *
   * If [child] is an [absolute path][isAbsolute] or [has a volume letter][hasVolumeLetter] then
   * this function is equivalent to `child.toPath()`.
   */
  operator fun div(child: String): Path

  /**
   * Returns a path that resolves [child] relative to this path. Note that the result isn't
   * guaranteed to be normalized even if this and [child] are both normalized themselves.
   *
   * If [child] is an [absolute path][isAbsolute] or [has a volume letter][hasVolumeLetter] then
   * this function is equivalent to `child.toPath()`.
   */
  operator fun div(child: ByteString): Path

  /**
   * Returns a path that resolves [child] relative to this path. Note that the result isn't
   * guaranteed to be normalized even if this and [child] are both normalized themselves.
   *
   * If [child] is an [absolute path][isAbsolute] or [has a volume letter][hasVolumeLetter] then
   * this function is equivalent to `child.toPath()`.
   */
  operator fun div(child: Path): Path

  /**
   * Returns a path that resolves [child] relative to this path.
   *
   * Set [normalize] to true to eagerly consume `..` segments on the resolved path. In all cases,
   * leading `..` on absolute paths will be removed. If [normalize] is false, note that the result
   * isn't guaranteed to be normalized even if this and [child] are both normalized themselves.
   *
   * If [child] is an [absolute path][isAbsolute] or [has a volume letter][hasVolumeLetter] then
   * this function is equivalent to `child.toPath(normalize)`.
   */
  fun resolve(child: String, normalize: Boolean = false): Path

  /**
   * Returns a path that resolves [child] relative to this path.
   *
   * Set [normalize] to true to eagerly consume `..` segments on the resolved path. In all cases,
   * leading `..` on absolute paths will be removed. If [normalize] is false, note that the result
   * isn't guaranteed to be normalized even if this and [child] are both normalized themselves.
   *
   * If [child] is an [absolute path][isAbsolute] or [has a volume letter][hasVolumeLetter] then
   * this function is equivalent to `child.toPath(normalize)`.
   */
  fun resolve(child: ByteString, normalize: Boolean = false): Path

  /**
   * Returns a path that resolves [child] relative to this path.
   *
   * Set [normalize] to true to eagerly consume `..` segments on the resolved path. In all cases,
   * leading `..` on absolute paths will be removed. If [normalize] is false, note that the result
   * isn't guaranteed to be normalized even if this and [child] are both normalized themselves.
   *
   * If [child] is an [absolute path][isAbsolute] or [has a volume letter][hasVolumeLetter] then
   * this function is equivalent to `child.toPath(normalize)`.
   */
  fun resolve(child: Path, normalize: Boolean = false): Path

  /**
   * Returns this path relative to [other]. This effectively inverts the resolve operator, `/`. For
   * any two paths `a` and `b` that have the same root, `a / (b.relativeTo(a))` is equal to `b`. If
   * both paths don't use the same slash, the resolved path will use the slash of the [other] path.
   *
   * @throws IllegalArgumentException if this path and the [other] path are not both
   * [absolute paths][isAbsolute] or both [relative paths][isRelative], or if they are both
   * [absolute paths][isAbsolute] but of different roots (C: vs D:, or C: vs \\server, etc.).
   * It will also throw if the relative path is impossible to resolve. For instance, it is
   * impossible to resolve the path `../a` relative to `../../b`.
   */
  @Throws(IllegalArgumentException::class)
  fun relativeTo(other: Path): Path

  /**
   * Returns the normalized version of this path. This has the same effect as
   * `this.toString().toPath(normalize = true)`.
   */
  fun normalized(): Path

  override fun compareTo(other: Path): Int

  override fun equals(other: Any?): Boolean

  override fun hashCode(): Int

  override fun toString(): String

  companion object {
    /**
     * Either `/` (on UNIX-like systems including Android, iOS, and Linux) or `\` (on Windows
     * systems).
     *
     * This separator is used by `FileSystem.SYSTEM` and possibly other file systems on the host
     * system. Some file system implementations may not use this separator.
     */
    val DIRECTORY_SEPARATOR: String

    /**
     * Returns the [Path] representation for this string.
     *
     * Set [normalize] to true to eagerly consume `..` segments in your path. In all cases, leading
     * `..` on absolute paths will be removed.
     *
     * ```
     * "/Users/jesse/Documents/../notes.txt".toPath(normalize = false).toString() => "/Users/jesse/Documents/../notes.txt"
     * "/Users/jesse/Documents/../notes.txt".toPath(normalize = true).toString() => "/Users/jesse/notes.txt"
     * ```
     */
    fun String.toPath(normalize: Boolean = false): Path
  }
}
