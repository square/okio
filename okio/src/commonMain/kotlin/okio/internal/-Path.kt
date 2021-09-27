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
package okio.internal

import kotlin.native.concurrent.SharedImmutable
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ExperimentalFileSystem
import okio.Path
import okio.Path.Companion.toPath

@SharedImmutable
private val SLASH = "/".encodeUtf8()

@SharedImmutable
private val BACKSLASH = "\\".encodeUtf8()

@SharedImmutable
private val ANY_SLASH = "/\\".encodeUtf8()

@SharedImmutable
private val DOT = ".".encodeUtf8()

@SharedImmutable
private val DOT_DOT = "..".encodeUtf8()

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonRoot(): Path? {
  return when (val rootLength = rootLength()) {
    -1 -> null
    else -> Path(bytes.substring(0, rootLength))
  }
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonSegments(): List<String> {
  return commonSegmentsBytes().map { it.utf8() }
}

/** This function skips the root then splits on slash. */
@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonSegmentsBytes(): List<ByteString> {
  val result = mutableListOf<ByteString>()
  var segmentStart = rootLength()

  // segmentStart should always follow a `\`, but for UNC paths it doesn't.
  if (segmentStart == -1) {
    segmentStart = 0
  } else if (segmentStart < bytes.size && bytes[segmentStart] == '\\'.code.toByte()) {
    segmentStart++
  }

  for (i in segmentStart until bytes.size) {
    if (bytes[i] == '/'.code.toByte() || bytes[i] == '\\'.code.toByte()) {
      result += bytes.substring(segmentStart, i)
      segmentStart = i + 1
    }
  }

  if (segmentStart < bytes.size) {
    result += bytes.substring(segmentStart, bytes.size)
  }

  return result
}

/** Return the length of the prefix of this that is the root path, or -1 if it has no root. */
@ExperimentalFileSystem
private fun Path.rootLength(): Int {
  if (bytes.size == 0) return -1
  if (bytes[0] == '/'.code.toByte()) return 1

  if (bytes[0] == '\\'.code.toByte()) {
    if (bytes.size > 2 && bytes[1] == '\\'.code.toByte()) {
      // Look for a root like `\\localhost`.
      var uncRootEnd = bytes.indexOf(BACKSLASH, fromIndex = 2)
      if (uncRootEnd == -1) uncRootEnd = bytes.size
      return uncRootEnd
    }

    // We found a root like `\`.
    return 1
  }

  // Look for a root like `C:\`.
  if (bytes.size > 2 && bytes[1] == ':'.code.toByte() && bytes[2] == '\\'.code.toByte()) {
    val c = bytes[0].toInt().toChar()
    if (c !in 'a'..'z' && c !in 'A'..'Z') return -1
    return 3
  }

  return -1
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonIsAbsolute(): Boolean {
  return rootLength() != -1
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonIsRelative(): Boolean {
  return rootLength() == -1
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonVolumeLetter(): Char? {
  if (bytes.indexOf(SLASH) != -1) return null
  if (bytes.size < 2) return null
  if (bytes[1] != ':'.code.toByte()) return null
  val c = bytes[0].toInt().toChar()
  if (c !in 'a'..'z' && c !in 'A'..'Z') return null
  return c
}

@ExperimentalFileSystem
private val Path.indexOfLastSlash: Int
  get() {
    val lastSlash = bytes.lastIndexOf(SLASH)
    if (lastSlash != -1) return lastSlash
    return bytes.lastIndexOf(BACKSLASH)
  }

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonNameBytes(): ByteString {
  val lastSlash = indexOfLastSlash
  return when {
    lastSlash != -1 -> bytes.substring(lastSlash + 1)
    volumeLetter != null && bytes.size == 2 -> ByteString.EMPTY // "C:" has no name.
    else -> bytes
  }
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonName(): String {
  return nameBytes.utf8()
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonParent(): Path? {
  if (bytes == DOT || bytes == SLASH || bytes == BACKSLASH || lastSegmentIsDotDot()) {
    return null // Terminal path.
  }

  val lastSlash = indexOfLastSlash
  when {
    lastSlash == 2 && volumeLetter != null -> {
      if (bytes.size == 3) return null // "C:\" has no parent.
      return Path(bytes.substring(endIndex = 3)) // Keep the trailing '\' in C:\.
    }
    lastSlash == 1 && bytes.startsWith(BACKSLASH) -> {
      return null // "\\server" is a UNC path with no parent.
    }
    lastSlash == -1 && volumeLetter != null -> {
      if (bytes.size == 2) return null // "C:" has no parent.
      return Path(bytes.substring(endIndex = 2)) // C: is volume-relative.
    }
    lastSlash == -1 -> {
      return Path(DOT) // Parent is the current working directory.
    }
    lastSlash == 0 -> {
      return Path(bytes.substring(endIndex = 1)) // Parent is the filesystem root '/'.
    }
    else -> {
      return Path(bytes.substring(endIndex = lastSlash))
    }
  }
}

@ExperimentalFileSystem
private fun Path.lastSegmentIsDotDot(): Boolean {
  if (bytes.endsWith(DOT_DOT)) {
    if (bytes.size == 2) return true // ".." is the whole string.
    if (bytes.rangeEquals(bytes.size - 3, SLASH, 0, 1)) return true // Ends with "/..".
    if (bytes.rangeEquals(bytes.size - 3, BACKSLASH, 0, 1)) return true // Ends with "\..".
  }
  return false
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonIsRoot(): Boolean {
  return rootLength() == bytes.size
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonResolve(child: String): Path {
  return div(Buffer().writeUtf8(child).toPath())
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonResolve(child: Path): Path {
  if (child.isAbsolute || child.volumeLetter != null) return child

  val slash = slash ?: child.slash ?: Path.DIRECTORY_SEPARATOR.toSlash()

  val buffer = Buffer()
  buffer.write(bytes)
  if (buffer.size > 0) {
    buffer.write(slash)
  }
  buffer.write(child.bytes)
  return buffer.toPath()
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonRelativeTo(other: Path): Path {
  require(isAbsolute && other.isAbsolute || isRelative && other.isRelative) {
    "Paths of different roots cannot be relative to each other: $this and $other"
  }

  val thisSlash = slash
  val otherSlash = other.slash
  require(thisSlash == otherSlash) {
    "Paths of different platforms cannot be relative to each other: $this and $other"
  }

  val slash = thisSlash ?: otherSlash ?: Path.DIRECTORY_SEPARATOR.toSlash()

  // We look at the path both have in common.
  var lastCommonByteIndex = 0
  while (lastCommonByteIndex < bytes.size &&
    lastCommonByteIndex < other.bytes.size &&
    (bytes[lastCommonByteIndex]) == (other.bytes[lastCommonByteIndex])
  ) {
    lastCommonByteIndex++
  }

  if (lastCommonByteIndex == bytes.size && bytes.size == other.bytes.size) {
    // `this` and `other` are the same path.
    return ".".toPath()
  }

  if (lastCommonByteIndex == 0 && isAbsolute) {
    throw IllegalArgumentException(
      "Paths of different roots cannot be relative to each other: $this and $other"
    )
  }

  val lastCommonSlashIndex = bytes.lastIndexOf(slash, lastCommonByteIndex)
  require(other.bytes.indexOf(DOT_DOT, lastCommonSlashIndex) == -1) {
    "Impossible relative path to resolve: $this and $other"
  }

  val firstDiffIndex = lastCommonSlashIndex + slash.size

  val buffer = Buffer()
  val stepsToCommonParent = other.bytes.substring(firstDiffIndex, other.bytes.size).count(slash)
  // We check if there is a trailing path segment on `other`.
  if (other.bytes.size > firstDiffIndex) {
    for (j in 0..stepsToCommonParent) {
      buffer.write(DOT_DOT)
      buffer.write(slash)
    }
  }

  buffer.write(bytes.substring(firstDiffIndex, bytes.size))
  return buffer.toPath()
}

@ExperimentalFileSystem
private val Path.slash: ByteString?
  get() {
    return when {
      bytes.indexOf(SLASH) != -1 -> SLASH
      bytes.indexOf(BACKSLASH) != -1 -> BACKSLASH
      else -> null
    }
  }

/** Returns the number of [value]s present in this [ByteString]. */
private fun ByteString.count(value: ByteString): Int {
  var count = 0
  var pos = 0
  while (pos < size) {
    when (val next = indexOf(value, pos)) {
      -1 -> return count
      else -> {
        pos = next + 1
        count++
      }
    }
  }
  return count
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonCompareTo(other: Path): Int {
  return bytes.compareTo(other.bytes)
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonEquals(other: Any?): Boolean {
  return other is Path && other.bytes == bytes
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonHashCode(): Int {
  return bytes.hashCode()
}

@ExperimentalFileSystem
@Suppress("NOTHING_TO_INLINE")
internal inline fun Path.commonToString(): String {
  return bytes.utf8()
}

@ExperimentalFileSystem
fun String.commonToPath(): Path {
  return Buffer().writeUtf8(this).toPath()
}

/** Consume the buffer and return it as a path. */
@ExperimentalFileSystem
internal fun Buffer.toPath(): Path {
  var slash: ByteString? = null
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
      -1L -> Path.DIRECTORY_SEPARATOR.toSlash()
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

  return Path(result.readByteString())
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
    '/'.code -> SLASH
    '\\'.code -> BACKSLASH
    else -> throw IllegalArgumentException("not a directory separator: $this")
  }
}

private fun Buffer.startsWithVolumeLetterAndColon(slash: ByteString): Boolean {
  if (slash != BACKSLASH) return false
  if (size < 2) return false
  if (get(1) != ':'.code.toByte()) return false
  val b = get(0).toInt().toChar()
  return b in 'a'..'z' || b in 'A'..'Z'
}
