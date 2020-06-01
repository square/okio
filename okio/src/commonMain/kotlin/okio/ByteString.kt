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

import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * An immutable sequence of bytes.
 *
 * Byte strings compare lexicographically as a sequence of **unsigned** bytes. That is, the byte
 * string `ff` sorts after `00`. This is counter to the sort order of the corresponding bytes,
 * where `-1` sorts before `0`.
 *
 * **Full disclosure:** this class provides untrusted input and output streams with raw access to
 * the underlying byte array. A hostile stream implementation could keep a reference to the mutable
 * byte string, violating the immutable guarantee of this class. For this reason a byte string's
 * immutability guarantee cannot be relied upon for security in applets and other environments that
 * run both trusted and untrusted code in the same process.
 */
expect open class ByteString
// Trusted internal constructor doesn't clone data.
internal constructor(data: ByteArray) : Comparable<ByteString> {
  internal val data: ByteArray

  internal var hashCode: Int
  internal var utf8: String?

  /** Constructs a new `String` by decoding the bytes as `UTF-8`.  */
  fun utf8(): String

  /**
   * Returns this byte string encoded as [Base64](http://www.ietf.org/rfc/rfc2045.txt). In violation
   * of the RFC, the returned string does not wrap lines at 76 columns.
   */
  fun base64(): String

  /** Returns this byte string encoded as [URL-safe Base64](http://www.ietf.org/rfc/rfc4648.txt). */
  fun base64Url(): String

  /** Returns this byte string encoded in hexadecimal.  */
  fun hex(): String

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'A' through 'Z' replaced
   * with the corresponding byte in 'a' through 'z'. Returns this byte string if it contains no
   * bytes in 'A' through 'Z'.
   */
  fun toAsciiLowercase(): ByteString

  /**
   * Returns a byte string that is a substring of this byte string, beginning at the specified
   * `beginIndex` and ends at the specified `endIndex`. Returns this byte string if `beginIndex` is
   * 0 and `endIndex` is the length of this byte string.
   */
  fun substring(beginIndex: Int = 0, endIndex: Int = size): ByteString

  /**
   * Returns a byte string equal to this byte string, but with the bytes 'a' through 'z' replaced
   * with the corresponding byte in 'A' through 'Z'. Returns this byte string if it contains no
   * bytes in 'a' through 'z'.
   */
  fun toAsciiUppercase(): ByteString

  /** Returns the byte at `pos`.  */
  internal fun internalGet(pos: Int): Byte

  /** Returns the byte at `index`.  */
  @JvmName("getByte")
  operator fun get(index: Int): Byte

  /** Returns the number of bytes in this ByteString. */
  val size: Int
    @JvmName("size") get

  // Hack to work around Kotlin's limitation for using JvmName on open/override vals/funs
  internal fun getSize(): Int

  /** Returns a byte array containing a copy of the bytes in this `ByteString`. */
  fun toByteArray(): ByteArray

  /** Writes the contents of this byte string to `buffer`.  */
  internal fun write(buffer: Buffer, offset: Int, byteCount: Int)

  /** Returns the bytes of this string without a defensive copy. Do not mutate!  */
  internal fun internalArray(): ByteArray

  /**
   * Returns true if the bytes of this in `[offset..offset+byteCount)` equal the bytes of `other` in
   * `[otherOffset..otherOffset+byteCount)`. Returns false if either range is out of bounds.
   */
  fun rangeEquals(offset: Int, other: ByteString, otherOffset: Int, byteCount: Int): Boolean

  /**
   * Returns true if the bytes of this in `[offset..offset+byteCount)` equal the bytes of `other` in
   * `[otherOffset..otherOffset+byteCount)`. Returns false if either range is out of bounds.
   */
  fun rangeEquals(offset: Int, other: ByteArray, otherOffset: Int, byteCount: Int): Boolean

  fun startsWith(prefix: ByteString): Boolean

  fun startsWith(prefix: ByteArray): Boolean

  fun endsWith(suffix: ByteString): Boolean

  fun endsWith(suffix: ByteArray): Boolean

  @JvmOverloads
  fun indexOf(other: ByteString, fromIndex: Int = 0): Int

  @JvmOverloads
  fun indexOf(other: ByteArray, fromIndex: Int = 0): Int

  fun lastIndexOf(other: ByteString, fromIndex: Int = size): Int

  fun lastIndexOf(other: ByteArray, fromIndex: Int = size): Int

  override fun equals(other: Any?): Boolean

  override fun hashCode(): Int

  override fun compareTo(other: ByteString): Int

  /**
   * Projects this value to the range `[0..size)` using linear interpolation. This is equivalent to
   * a sorted partitioning of all possible byte strings across [size] equally-sized buckets and
   * returning the index of the bucket that this byte string fits in.
   *
   * For example, the byte string `8000` is the median of all 2-element byte strings, and calling
   * `toIndex(100)` on it returns 50. Some other examples:
   *
   * | Byte String (hex)  | `toIndex(100)` | `toIndex(256)` | `toIndex(Int.MAX_VALUE)` |
   * | :----------------- | -------------: | -------------: | -----------------------: |
   * | (empty)            |              0 |              0 |                        0 |
   * | 00                 |              0 |              0 |                        0 |
   * | 0000               |              0 |              0 |                        0 |
   * | 000000             |              0 |              0 |                        0 |
   * | 0000000001         |              0 |              0 |                        0 |
   * | 00000001           |              0 |              0 |                        0 |
   * | 00000002           |              0 |              0 |                        0 |
   * | 00000003           |              0 |              0 |                        1 |
   * | 01                 |              0 |              1 |                  8388607 |
   * | 02                 |              0 |              2 |                 16777215 |
   * | 03                 |              1 |              3 |                 25165823 |
   * | 80                 |             50 |            128 |               1073741823 |
   * | 8000               |             50 |            128 |               1073741823 |
   * | 80000000           |             50 |            128 |               1073741823 |
   * | 81                 |             50 |            129 |               1082130431 |
   * | 81ffffff           |             50 |            129 |               1090519038 |
   * | 82                 |             50 |            130 |               1090519039 |
   * | 83                 |             51 |            131 |               1098907647 |
   * | ff                 |             99 |            255 |               2139095039 |
   * | ffff               |             99 |            255 |               2147450879 |
   * | ffffffff           |             99 |            255 |               2147483646 |
   * | ffffffffffff       |             99 |            255 |               2147483646 |
   *
   * This interprets the bytes in this byte string as **unsigned**. This behavior is consistent with
   * [compareTo]. The returned value is also consistent with [compareTo] though the dynamic range
   * is compressed. For two byte strings `a` and `b`, if `a < b`, then
   * `a.toIndex(n) <= b.toIndex(n)` for all sizes `n`.
   *
   * This examines at most the first 4 bytes of this byte string. Data beyond the first 4 bytes is
   * not used to compute the result.
   *
   * @param size a positive integer.
   * @return a value that is greater than or equal to `0` and less than [size].
   */
  fun toIndex(size: Int): Int

  /**
   * Projects this value to the range `[0.0..1.0)` using linear interpolation. This is equivalent to
   * sorting all possible byte strings and returning the fraction that precede this byte string.
   *
   * For example, the byte string `8000` is the median of all 2-element byte strings, and calling
   * `toFraction()` on it returns 0.5. Some other examples:
   *
   * | Byte String (hex)  | `toFraction()`     |
   * | :----------------- | :----------------- |
   * | (empty)            | 0.0                |
   * | 00                 | 0.0                |
   * | 0000               | 0.0                |
   * | 000000             | 0.0                |
   * | 00000000000001     | 0.0                |
   * | 00000000000007     | 0.0                |
   * | 00000000000008     | 0.0000000000000001 |
   * | 0000000001         | 0.0000000000009094 |
   * | 00000001           | 0.0000000002328306 |
   * | 01                 | 0.00390625         |
   * | 02                 | 0.0078125          |
   * | 03                 | 0.01171875         |
   * | 80                 | 0.5                |
   * | 8000               | 0.5                |
   * | 80000000000000     | 0.5                |
   * | 81                 | 0.50390625         |
   * | 81ffffff           | 0.5078124997671694 |
   * | 82                 | 0.5078125          |
   * | 83                 | 0.51171875         |
   * | ff                 | 0.99609375         |
   * | ffff               | 0.9999847412109375 |
   * | ffffffff           | 0.9999999997671694 |
   * | ffffffffffff       | 0.9999999999999964 |
   * | ffffffffffffff     | 0.9999999999999999 |
   *
   * This interprets the bytes in this byte string as **unsigned**. This behavior is consistent with
   * [compareTo]. The returned value is also consistent with [compareTo] though the dynamic range
   * is compressed. For two byte strings `a` and `b`, if `a < b`, then
   * `a.toFraction() <= b.toFraction()`.
   *
   * This examines at most the first 7 bytes of this byte string. Data beyond the first 7 bytes is
   * not used to compute the result.
   *
   * @return a value that is greater than or equal to `0.0` and less than `1.0`.
   */
  fun toFraction(): Double

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  override fun toString(): String

  companion object {
    /** A singleton empty `ByteString`.  */
    @JvmField
    val EMPTY: ByteString

    /** Returns a new byte string containing a clone of the bytes of `data`. */
    @JvmStatic
    fun of(vararg data: Byte): ByteString

    /**
     * Returns a new [ByteString] containing a copy of `byteCount` bytes of this [ByteArray]
     * starting at `offset`.
     */
    @JvmStatic
    fun ByteArray.toByteString(offset: Int = 0, byteCount: Int = size): ByteString

    /** Returns a new byte string containing the `UTF-8` bytes of this [String].  */
    @JvmStatic
    fun String.encodeUtf8(): ByteString

    /**
     * Decodes the Base64-encoded bytes and returns their value as a byte string. Returns null if
     * this is not a Base64-encoded sequence of bytes.
     */
    @JvmStatic
    fun String.decodeBase64(): ByteString?

      /** Decodes the hex-encoded bytes and returns their value a byte string.  */
    @JvmStatic
    fun String.decodeHex(): ByteString
  }
}
