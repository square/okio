/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import okio.common.COMMON_EMPTY
import okio.common.COMMON_HEX_DIGITS
import okio.common.commonBase64
import okio.common.commonBase64Url
import okio.common.commonCompareTo
import okio.common.commonDecodeBase64
import okio.common.commonDecodeHex
import okio.common.commonEncodeUtf8
import okio.common.commonEndsWith
import okio.common.commonEquals
import okio.common.commonGetByte
import okio.common.commonGetSize
import okio.common.commonHashCode
import okio.common.commonHex
import okio.common.commonInternalArray
import okio.common.commonOf
import okio.common.commonRangeEquals
import okio.common.commonStartsWith
import okio.common.commonSubstring
import okio.common.commonToAsciiLowercase
import okio.common.commonToAsciiUppercase
import okio.common.commonToByteArray
import okio.common.commonUtf8

/**
 * An immutable sequence of bytes.
 *
 * Byte strings compare lexicographically as a sequence of **unsigned** bytes. That is, the byte
 * string `ff` sorts after `00`. This is counter to the sort order of the corresponding bytes,
 * where `-1` sorts before `0`.
 */
actual open class ByteString
// Trusted internal constructor doesn't clone data.
internal actual constructor(
  internal actual val data: ByteArray
) : Comparable<ByteString> {
  internal actual var hashCode: Int = 0 // Lazily computed; 0 if unknown.
  internal actual var utf8: String? = null // Lazily computed.

  /** Constructs a new `String` by decoding the bytes as `UTF-8`.  */
  actual open fun utf8(): String = commonUtf8()

  /**
   * Returns this byte string encoded as [Base64](http://www.ietf.org/rfc/rfc2045.txt). In violation
   * of the RFC, the returned string does not wrap lines at 76 columns.
   */
  actual open fun base64(): String = commonBase64()

  /** Returns this byte string encoded as [URL-safe Base64](http://www.ietf.org/rfc/rfc4648.txt). */
  actual open fun base64Url(): String = commonBase64Url()

  /** Returns this byte string encoded in hexadecimal.  */
  actual open fun hex(): String = commonHex()

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'A' through 'Z' replaced
   * with the corresponding byte in 'a' through 'z'. Returns this byte string if it contains no
   * bytes in 'A' through 'Z'.
   */
  actual open fun toAsciiLowercase(): ByteString = commonToAsciiLowercase()

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'a' through 'z' replaced
   * with the corresponding byte in 'A' through 'Z'. Returns this byte string if it contains no
   * bytes in 'a' through 'z'.
   */
  actual open fun toAsciiUppercase(): ByteString = commonToAsciiUppercase()

  /**
   * Returns a byte string that is a substring of this byte string, beginning at the specified
   * index until the end of this string. Returns this byte string if `beginIndex` is 0.
   */
  actual open fun substring(beginIndex: Int): ByteString = commonSubstring(beginIndex, data.size)

  /**
   * Returns a byte string that is a substring of this byte string, beginning at the specified
   * `beginIndex` and ends at the specified `endIndex`. Returns this byte string if
   * `beginIndex` is 0 and `endIndex` is the length of this byte string.
   */
  actual open fun substring(beginIndex: Int, endIndex: Int): ByteString =
      commonSubstring(beginIndex, endIndex)

  /** Returns the byte at `pos`.  */
  internal actual open fun getByte(pos: Int) = commonGetByte(pos)

  /** Returns the byte at `index`.  */
  @JvmName("getByte")
  actual operator fun get(index: Int): Byte = getByte(index)

  /** Returns the number of bytes in this ByteString. */
  actual val size
    @JvmName("size") get() = getSize()

  // Hack to work around Kotlin's limitation for using JvmName on open/override vals/funs
  internal actual open fun getSize() = commonGetSize()

  /** Returns a byte array containing a copy of the bytes in this `ByteString`. */
  actual open fun toByteArray() = commonToByteArray()

  /** Returns the bytes of this string without a defensive copy. Do not mutate!  */
  internal actual open fun internalArray() = commonInternalArray()

  /**
   * Returns true if the bytes of this in `[offset..offset+byteCount)` equal the bytes of `other` in
   * `[otherOffset..otherOffset+byteCount)`. Returns false if either range is out of bounds.
   */
  actual open fun rangeEquals(
    offset: Int,
    other: ByteString,
    otherOffset: Int,
    byteCount: Int
  ): Boolean = commonRangeEquals(offset, other, otherOffset, byteCount)

  /**
   * Returns true if the bytes of this in `[offset..offset+byteCount)` equal the bytes of `other` in
   * `[otherOffset..otherOffset+byteCount)`. Returns false if either range is out of bounds.
   */
  actual open fun rangeEquals(
    offset: Int,
    other: ByteArray,
    otherOffset: Int,
    byteCount: Int
  ): Boolean = commonRangeEquals(offset, other, otherOffset, byteCount)

  actual fun startsWith(prefix: ByteString) = commonStartsWith(prefix)

  actual fun startsWith(prefix: ByteArray) = commonStartsWith(prefix)

  actual fun endsWith(suffix: ByteString) = commonEndsWith(suffix)

  actual fun endsWith(suffix: ByteArray) = commonEndsWith(suffix)

  // TODO move indexOf() when https://youtrack.jetbrains.com/issue/KT-24357 is fixed

  @JvmOverloads
  fun indexOf(other: ByteString, fromIndex: Int = 0) = indexOf(other.internalArray(), fromIndex)

  @JvmOverloads
  open fun indexOf(other: ByteArray, fromIndex: Int = 0): Int {
    var fromIndex = fromIndex
    fromIndex = maxOf(fromIndex, 0)
    var i = fromIndex
    val limit = data.size - other.size
    while (i <= limit) {
      if (arrayRangeEquals(data, i, other, 0, other.size)) {
        return i
      }
      i++
    }
    return -1
  }

  // TODO move lastIndexOf() when https://youtrack.jetbrains.com/issue/KT-24356
  // and https://youtrack.jetbrains.com/issue/KT-24356 are fixed

  @JvmOverloads
  fun lastIndexOf(other: ByteString, fromIndex: Int = size) = lastIndexOf(other.internalArray(),
      fromIndex)

  @JvmOverloads
  open fun lastIndexOf(other: ByteArray, fromIndex: Int = size): Int {
    var fromIndex = fromIndex
    fromIndex = minOf(fromIndex, data.size - other.size)
    for (i in fromIndex downTo 0) {
      if (arrayRangeEquals(data, i, other, 0, other.size)) {
        return i
      }
    }
    return -1
  }

  actual override fun equals(other: Any?) = commonEquals(other)

  actual override fun hashCode() = commonHashCode()

  actual override fun compareTo(other: ByteString) = commonCompareTo(other)

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  actual override fun toString(): String = data.toUtf8String()

  actual companion object {
    internal actual val HEX_DIGITS = COMMON_HEX_DIGITS

    /** A singleton empty `ByteString`.  */
    @JvmField
    actual val EMPTY: ByteString = COMMON_EMPTY

    /** Returns a new byte string containing a clone of the bytes of `data`. */
    @JvmStatic
    actual fun of(vararg data: Byte) = commonOf(*data.copyOf())

    // TODO move toByteString() when https://youtrack.jetbrains.com/issue/KT-24356 is fixed

    /**
     * Returns a new [ByteString] containing a copy of `byteCount` bytes of this [ByteArray]
     * starting at `offset`.
     */
    @JvmStatic
    @JvmName("of")
    fun ByteArray.toByteString(offset: Int = 0, byteCount: Int = size): ByteString {
      checkOffsetAndCount(size.toLong(), offset.toLong(), byteCount.toLong())

      val copy = ByteArray(byteCount)
      arraycopy(this, offset, copy, 0, byteCount)
      return ByteString(copy)
    }

    /** Returns a new byte string containing the `UTF-8` bytes of this [String].  */
    @JvmStatic
    actual fun String.encodeUtf8(): ByteString = commonEncodeUtf8()

    /**
     * Decodes the Base64-encoded bytes and returns their value as a byte string. Returns null if
     * this is not a Base64-encoded sequence of bytes.
     */
    @JvmStatic
    actual fun String.decodeBase64(): ByteString? = commonDecodeBase64()

    /** Decodes the hex-encoded bytes and returns their value a byte string.  */
    @JvmStatic
    actual fun String.decodeHex() = commonDecodeHex()
  }
}
