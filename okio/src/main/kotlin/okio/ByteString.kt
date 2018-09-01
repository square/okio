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
expect class ByteString
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

  fun indexOf(other: ByteString, fromIndex: Int = 0): Int

  fun indexOf(other: ByteArray, fromIndex: Int = 0): Int

  override fun equals(other: Any?): Boolean

  override fun hashCode(): Int

  override fun compareTo(other: ByteString): Int

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  override fun toString(): String

  companion object {
    // TODO null in JS and Native - https://youtrack.jetbrains.com/issue/KT-26497
    /** A singleton empty `ByteString`.  */
    @JvmField
    val EMPTY: ByteString

    /** Returns a new byte string containing a clone of the bytes of `data`. */
    @JvmStatic
    fun of(vararg data: Byte): ByteString

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
