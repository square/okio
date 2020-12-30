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

import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * A hierarchical address on a filesystem. A path is an identifier only; a [Filesystem] is required
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
 *   location on a filesystem; they are relative to the system's current working directory. Use
 *   [Filesystem.canonicalize] to convert a relative path to its absolute path on a particular
 *   filesystem.
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
 *  * If the segment is `..`, then the the path must be relative. All `..` segments precede all
 *    other segments.
 *
 * The only path that ends with `/` is the filesystem root, `/`. The dot path `.` is a relative
 * path that resolves to whichever path it is resolved against.
 *
 * The [name] is the last segment in a path. It is typically a file or directory name, like
 * `README.md` or `Desktop`. The name may be another special value:
 *
 *  * The empty string is the name of the file system root path (full path `/`).
 *  * `.` is the name of the identity relative path (full path `.`).
 *  * `..` is the name of a path consisting of only `..` segments (such as `../../..`).
 *
 * Sample Paths
 * ------------
 *
 * <table>
 * <tr><th> Path                         <th> Parent             <th> Name          <th> Notes                          </tr>
 * <tr><td> `/`                          <td> null               <td> (empty)       <td> root                           </tr>
 * <tr><td> `/home/jesse/notes.txt`      <td> `/home/jesse`      <td> `notes.txt`   <td> absolute path                  </tr>
 * <tr><td> `project/notes.txt`          <td> `project`          <td> `notes.txt`   <td> relative path                  </tr>
 * <tr><td> `../../project/notes.txt`    <td> `../../project`    <td> `notes.txt`   <td> relative path with traversal   </tr>
 * <tr><td> `../../..`                   <td> null               <td> `..`          <td> relative path with traversal   </tr>
 * <tr><td> `.`                          <td> null               <td> `.`           <td> current working directory      </tr>
 * <tr><td> `C:\`                        <td> null               <td> (empty)       <td> volume root (Windows)          </tr>
 * <tr><td> `C:\Windows\notepad.exe`     <td> `C:\Windows`       <td> `notepad.exe` <td> volume absolute path (Windows) </tr>
 * <tr><td> `\`                          <td> null               <td> (empty)       <td> absolute path (Windows)        </tr>
 * <tr><td> `\Windows\notepad.exe`       <td> `\Windows`         <td> `notepad.exe` <td> absolute path (Windows)        </tr>
 * <tr><td> `C:`                         <td> null               <td> (empty)       <td> volume-relative path (Windows) </tr>
 * <tr><td> `C:project\notes.txt`        <td> `C:project`        <td> `notes.txt`   <td> volume-relative path (Windows) </tr>
 * <tr><td> `\\server`                   <td> null               <td> `server`      <td> UNC server (Windows)           </tr>
 * <tr><td> `\\server\project\notes.txt` <td> `\\server\project` <td> `notes.txt`   <td> UNC absolute path (Windows)    </tr>
 * </table>
 */
@ExperimentalFilesystem
class Path private constructor(
  private val slash: ByteString,
  private val bytes: ByteString
) {
  init {
    require(slash == SLASH || slash == BACKSLASH)
  }

  val isAbsolute: Boolean
    get() = bytes.startsWith(slash) ||
      (volumeLetter != null && bytes.size > 2 && bytes[2] == '\\'.toByte())

  val isRelative: Boolean
    get() = !isAbsolute

  /**
   * This is the volume letter like "C" on Windows paths that starts with a volume letter. For
   * example, on the path "C:\Windows" this returns "C". This property is null if this is not a
   * Windows path, or if it doesn't have a volume letter.
   *
   * Note that paths that start with a volume letter are not necessarily absolute paths. For
   * example, the path "C:notepad.exe" is relative to whatever the current working directory is on
   * the C: drive.
   */
  @get:JvmName("volumeLetter")
  val volumeLetter: Char?
    get() {
      if (slash != BACKSLASH) return null
      if (bytes.size < 2) return null
      if (bytes[1] != ':'.toByte()) return null
      val c = bytes[0].toChar()
      if (c !in 'a'..'z' && c !in 'A'..'Z') return null
      return c
    }

  @get:JvmName("nameBytes")
  val nameBytes: ByteString
    get() {
      val lastSlash = bytes.lastIndexOf(slash)
      return when {
        lastSlash != -1 -> bytes.substring(lastSlash + 1)
        volumeLetter != null && bytes.size == 2 -> EMPTY // "C:" has no name.
        else -> bytes
      }
    }

  @get:JvmName("name")
  val name: String
    get() = nameBytes.utf8()

  /**
   * Returns the path immediately enclosing this path.
   *
   * This returns null if this has no parent. That includes these paths:
   *
   *  * The filesystem root (`/`)
   *  * The identity relative path (`.`)
   *  * A Windows volume root (like `C:\`)
   *  * A Windows Universal Naming Convention (UNC) root path (`\\server`)
   *  * A reference to the current working directory on a Windows volume (`C:`).
   *  * A series of relative paths (like `..` and `../..`).
   */
  @get:JvmName("parent")
  val parent: Path?
    get() {
      if (bytes == DOT || bytes == slash || lastSegmentIsDotDot()) {
        return null // Terminal path.
      }

      val lastSlash = bytes.lastIndexOf(slash)
      when {
        lastSlash == 2 && volumeLetter != null -> {
          if (bytes.size == 3) return null // "C:\" has no parent.
          return Path(slash, bytes.substring(endIndex = 3)) // Keep the trailing '\' in C:\.
        }
        lastSlash == 1 && bytes.startsWith(BACKSLASH) -> {
          return null // "\\server" is a UNC path with no parent.
        }
        lastSlash == -1 && volumeLetter != null -> {
          if (bytes.size == 2) return null // "C:" has no parent.
          return Path(slash, bytes.substring(endIndex = 2)) // C: is volume-relative.
        }
        lastSlash == -1 -> {
          return Path(slash, DOT) // Parent is the current working directory.
        }
        lastSlash == 0 -> {
          return Path(slash, bytes.substring(endIndex = 1)) // Parent is the filesystem root '/'.
        }
        else -> {
          return Path(slash, bytes.substring(endIndex = lastSlash))
        }
      }
    }

  /**
   * Returns true if this is an absolute path with no parent. UNIX paths have a single root, `/`.
   * Each volume on Windows is its own root, like `C:\` and `D:\`. Windows UNC paths like `\\server`
   * are also roots.
   */
  val isRoot: Boolean
    get() = parent == null && isAbsolute

  private fun lastSegmentIsDotDot(): Boolean {
    if (bytes.endsWith(DOT_DOT)) {
      if (bytes.size == 2) return true // ".." is the whole string.
      if (bytes.rangeEquals(bytes.size - 3, slash, 0, 1)) return true // Ends with "/.." or "\..".
    }
    return false
  }

  /**
   * Returns a path that resolves [child] relative to this path.
   *
   * If [child] is an [absolute path][isAbsolute] or [has a volume letter][hasVolumeLetter] then
   * this function is equivalent to `child.toPath()`.
   */
  @JvmName("resolve")
  operator fun div(child: String): Path {
    return div(Buffer().writeUtf8(child).toPath(slash))
  }

  /**
   * Returns a path that resolves [child] relative to this path.
   *
   * If [child] is an [absolute path][isAbsolute] or [has a volume letter][hasVolumeLetter] then
   * this function is equivalent to `child.toPath()`.
   */
  @JvmName("resolve")
  operator fun div(child: Path): Path {
    if (child.isAbsolute || child.volumeLetter != null) return child

    val buffer = Buffer()
    buffer.write(bytes)
    if (buffer.size > 0) {
      buffer.write(slash)
    }
    buffer.write(child.bytes)
    return buffer.toPath(directorySeparator = slash)
  }

  override fun equals(other: Any?): Boolean {
    return other is Path && other.bytes == bytes && other.slash == slash
  }

  override fun hashCode() = bytes.hashCode() xor slash.hashCode()

  override fun toString() = bytes.utf8()

  companion object {
    private val SLASH = "/".encodeUtf8()
    private val BACKSLASH = "\\".encodeUtf8()
    private val ANY_SLASH = "/\\".encodeUtf8()
    private val DOT = ".".encodeUtf8()
    private val DOT_DOT = "..".encodeUtf8()

    val directorySeparator = DIRECTORY_SEPARATOR

    @JvmName("get") @JvmOverloads @JvmStatic
    fun String.toPath(directorySeparator: String? = null): Path =
      Buffer().writeUtf8(this).toPath(directorySeparator?.toSlash())

    /** Consume the buffer and return it as a path. */
    internal fun Buffer.toPath(directorySeparator: ByteString? = null): Path {
      var slash = directorySeparator
      val result = Buffer()

      // Consume the absolute path prefix, like `/`, `\\`, `C:`, or `C:\` and write the
      // canonicalized prefix to result.
      var leadingSlashCount = 0
      while (rangeEquals(0L, SLASH) || rangeEquals(0L, BACKSLASH)) {
        val byte = readByte()
        slash = slash ?: byte.toSlash()
        leadingSlashCount++
      }
      if (leadingSlashCount >= 2 && slash == BACKSLASH) {
        // This is a Windows UNC path, like \\server\directory\file.txt.
        result.write(slash)
        result.write(slash)
      } else if (leadingSlashCount > 0) {
        // This is platform-dependent:
        //  * On UNIX: a absolute path like /home
        //  * On Windows: this is relative to the current volume, like \Windows.
        result.write(slash!!)
      } else {
        // This path doesn't start with any slash. We must initialize the slash character to use.
        val limit = indexOfElement(ANY_SLASH)
        slash = slash ?: when (limit) {
          -1L -> DIRECTORY_SEPARATOR.toSlash()
          else -> get(limit).toSlash()
        }
        if (startsWithVolumeLetterAndColon(slash)) {
          if (limit == 2L) {
            result.write(this, 3L) // Absolute on a named volume, like `C:\`.
          } else {
            result.write(this, 2L) // Relative to the named volume, like `C:`.
          }
        }
      }

      val absolute = result.size > 0

      val canonicalParts = mutableListOf<ByteString>()
      while (!exhausted()) {
        val limit = indexOfElement(ANY_SLASH)

        val part: ByteString
        if (limit == -1L) {
          part = readByteString()
        } else {
          part = readByteString(limit)
          readByte()
        }

        if (part == DOT_DOT) {
          if (!absolute && (canonicalParts.isEmpty() || canonicalParts.last() == DOT_DOT)) {
            canonicalParts.add(part) // '..' doesn't pop '..' for relative paths.
          } else {
            canonicalParts.removeLastOrNull()
          }
        } else if (part != DOT && part != ByteString.EMPTY) {
          canonicalParts.add(part)
        }
      }

      for (i in 0 until canonicalParts.size) {
        if (i > 0) result.write(slash)
        result.write(canonicalParts[i])
      }
      if (result.size == 0L) {
        result.write(DOT)
      }

      return Path(slash, result.readByteString())
    }

    private fun String.toSlash(): ByteString {
      return when (this) {
        "/" -> SLASH
        "\\" -> BACKSLASH
        else -> throw IllegalArgumentException("not a directory separator: $this")
      }
    }

    private fun Byte.toSlash(): ByteString {
      return when (toInt()) {
        '/'.toInt() -> SLASH
        '\\'.toInt() -> BACKSLASH
        else -> throw IllegalArgumentException("not a directory separator: $this")
      }
    }

    private fun Buffer.startsWithVolumeLetterAndColon(slash: ByteString): Boolean {
      if (slash != BACKSLASH) return false
      if (size < 2) return false
      if (get(1) != ':'.toByte()) return false
      val b = get(0).toChar()
      return b in 'a'..'z' || b in 'A'..'Z'
    }
  }
}
