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

import okio.internal.COMMON_EMPTY
import okio.internal.commonBase64
import okio.internal.commonBase64Url
import okio.internal.commonCompareTo
import okio.internal.commonDecodeBase64
import okio.internal.commonDecodeHex
import okio.internal.commonEncodeUtf8
import okio.internal.commonEndsWith
import okio.internal.commonEquals
import okio.internal.commonGetByte
import okio.internal.commonGetSize
import okio.internal.commonHashCode
import okio.internal.commonHex
import okio.internal.commonIndexOf
import okio.internal.commonInternalArray
import okio.internal.commonLastIndexOf
import okio.internal.commonOf
import okio.internal.commonRangeEquals
import okio.internal.commonStartsWith
import okio.internal.commonSubstring
import okio.internal.commonToAsciiLowercase
import okio.internal.commonToAsciiUppercase
import okio.internal.commonToByteArray
import okio.internal.commonToString
import okio.internal.commonUtf8

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

  // TODO move substring() when https://youtrack.jetbrains.com/issue/KT-22818 is fixed

  /**
   * Returns a byte string that is a substring of this byte string, beginning at the specified
   * `beginIndex` and ends at the specified `endIndex`. Returns this byte string if `beginIndex` is
   * 0 and `endIndex` is the length of this byte string.
   */
  open fun substring(beginIndex: Int = 0, endIndex: Int = size): ByteString =
      commonSubstring(beginIndex, endIndex)

  /** Returns the byte at `pos`.  */
  internal actual open fun internalGet(pos: Int): Byte {
    if (pos >= size || pos < 0) throw ArrayIndexOutOfBoundsException("size=$size pos=$pos")
    return commonGetByte(pos)
  }

  /** Returns the byte at `index`.  */
  actual operator fun get(index: Int): Byte = internalGet(index)

  /** Returns the number of bytes in this ByteString. */
  actual val size
    get() = getSize()

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

  actual fun indexOf(other: ByteString, fromIndex: Int) = indexOf(other.internalArray(), fromIndex)

  actual open fun indexOf(other: ByteArray, fromIndex: Int) = commonIndexOf(other, fromIndex)

  // TODO move lastIndexOf() when https://youtrack.jetbrains.com/issue/KT-22818 is fixed

  fun lastIndexOf(other: ByteString, fromIndex: Int = size) = lastIndexOf(other.internalArray(), fromIndex)

  fun lastIndexOf(other: ByteArray, fromIndex: Int = size) = commonLastIndexOf(other, fromIndex)

  actual override fun equals(other: Any?) = commonEquals(other)

  actual override fun hashCode() = commonHashCode()

  actual override fun compareTo(other: ByteString) = commonCompareTo(other)

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  actual override fun toString() = commonToString()

  actual companion object {
    /** A singleton empty `ByteString`.  */
    actual val EMPTY: ByteString = COMMON_EMPTY

    /** Returns a new byte string containing a clone of the bytes of `data`. */
    actual fun of(vararg data: Byte) = commonOf(data)

    // TODO move toByteString() when https://youtrack.jetbrains.com/issue/KT-22818 is fixed

    /**
     * Returns a new [ByteString] containing a copy of `byteCount` bytes of this [ByteArray]
     * starting at `offset`.
     */
    fun ByteArray.toByteString(offset: Int = 0, byteCount: Int = size): ByteString {
      checkOffsetAndCount(size.toLong(), offset.toLong(), byteCount.toLong())

      val copy = ByteArray(byteCount)
      arraycopy(this, offset, copy, 0, byteCount)
      return ByteString(copy)
    }

    /** Returns a new byte string containing the `UTF-8` bytes of this [String].  */
    actual fun String.encodeUtf8(): ByteString = commonEncodeUtf8()

    /**
     * Decodes the Base64-encoded bytes and returns their value as a byte string. Returns null if
     * this is not a Base64-encoded sequence of bytes.
     */
    actual fun String.decodeBase64(): ByteString? = commonDecodeBase64()

    /** Decodes the hex-encoded bytes and returns their value a byte string.  */
    actual fun String.decodeHex() = commonDecodeHex()
  }
}
