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

import okio.ByteString.Companion.encodeUtf8

/**
 * A hierarchical address on a filesystem. A path is an identifier only; a [Filesystem] is required
 * to access the file that a path refers to, if any.
 *
 * Paths may be absolute or relative:
 *
 * * **Absolute paths** are prefixed with `/` and identify a location independent of any working
 *   directory.
 * * **Relative paths** are not prefixed with `/`. On their own, relative paths do not identify a
 *   location on a filesystem; they must be resolved against an absolute path first. When a relative
 *   path is used to access a [Filesystem], it is resolved against [Filesystem.baseDirectory].
 *
 * After the optional leading `/`, the rest of the path is a sequence of segments separated by `/`
 * characters. Segments satisfy these rules:
 *
 *  * Segments are always non-empty.
 *  * If the segment is `.`, the full path is also `.`.
 *  * If the segment is `..`, the path is relative. All `..` segments precede all other segments.
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
 * | Path                   | Type       | Parent              | Name          |
 * | :--------------------- | :--------- | :------------------ | :------------ |
 * | `/Users/jessewilson`   | Absolute   | `/Users`            | `jessewilson` |
 * | `/Users`               | Absolute   | `/`                 | `Users`       |
 * | `/`                    | Absolute   | null                | (empty)       |
 * | `src/main/java`        | Relative   | `src/main`          | `java`        |
 * | `src/main`             | Relative   | `src`               | `main`        |
 * | `src`                  | Relative   | `.`                 | `src`         |
 * | `.`                    | Relative   | null                | `.`           |
 * | `../../src/main/java`  | Relative   | `../../src/main`    | `java`        |
 * | `../../src/main`       | Relative   | `../../src`         | `main`        |
 * | `../../src`            | Relative   | `../..`             | `src`         |
 * | `../..`                | Relative   | null                | `..`          |
 */
class Path private constructor(
  private val bytes: ByteString
) {
  val isAbsolute: Boolean
    get() = bytes.startsWith(SLASH)

  val isRelative: Boolean
    get() = !isAbsolute

  val nameBytes: ByteString
    get() {
      val lastSlash = bytes.lastIndexOf(SLASH)
      return when {
        lastSlash != -1 -> bytes.substring(lastSlash + 1)
        else -> bytes
      }
    }

  val name: String
    get() = nameBytes.utf8()

  /**
   * Returns the path immediately enclosing this path. This returns null if this is either the
   * filesystem root (`/`), the identity relative path (`.`), or a series of relative paths (`..`).
   */
  val parent: Path?
    get() {
      if (bytes == DOT || bytes == SLASH || bytes.endsWith(SLASH_DOT_DOT) || bytes == DOT_DOT) {
        return null // Terminal path.
      }
      return when (val lastSlash = bytes.lastIndexOf(SLASH)) {
        -1 -> Path(DOT) // Parent is the current working directory.
        0 -> Path(bytes.substring(endIndex = 1)) // Parent is the filesystem root '/'.
        else -> Path(bytes.substring(endIndex = lastSlash))
      }
    }

  /**
   * Returns a path that resolves [child] relative to this path. If [child] starts with `/` it is
   * an absolute path and this function is equivalent to `child.toPath()`.
   */
  operator fun div(child: String): Path {
    return div(child.toPath())
  }

  /**
   * Returns a path that resolves [child] relative to this path. If [child] starts with `/` it is
   * an absolute path and this function is equivalent to `child.toPath()`.
   */
  operator fun div(child: Path): Path {
    val buffer = Buffer()
    if (child.isRelative) {
      buffer.write(bytes)
      if (buffer.size > 0) {
        buffer.write(SLASH)
      }
    }
    buffer.write(child.bytes)
    return buffer.toPath()
  }

  override fun equals(other: Any?) = other is Path && other.bytes == bytes

  override fun hashCode() = bytes.hashCode()

  override fun toString() = bytes.utf8()

  companion object {
    private val SLASH = "/".encodeUtf8()
    private val DOT = ".".encodeUtf8()
    private val DOT_DOT = "..".encodeUtf8()
    private val SLASH_DOT_DOT = "/..".encodeUtf8()

    fun String.toPath(): Path = Buffer().writeUtf8(this).toPath()

    val directorySepartor = PLATFORM_SEPARATOR

    /** Consume the buffer and return it as a path. */
    internal fun Buffer.toPath(): Path {
      val absolute = !exhausted() && get(0) == '/'.toByte()
      if (absolute) {
        readByte()
      }

      val canonicalParts = mutableListOf<ByteString>()
      while (!exhausted()) {
        val limit = indexOf(SLASH)

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

      val result = Buffer()
      if (absolute) {
        result.write(SLASH)
      }
      for (i in 0 until canonicalParts.size) {
        if (i > 0) result.write(SLASH)
        result.write(canonicalParts[i])
      }
      if (result.size == 0L) {
        result.write(DOT)
      }

      return Path(result.readByteString())
    }
  }
}
