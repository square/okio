/*
 * Copyright 2014 Square Inc.
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
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
actual open class ByteString
// Trusted internal constructor doesn't clone data.
internal actual constructor(
  internal actual val data: ByteArray
) : Serializable, Comparable<ByteString> {
  @Transient internal actual var hashCode: Int = 0 // Lazily computed; 0 if unknown.
  @Transient internal actual var utf8: String? = null // Lazily computed.

  /** Constructs a new `String` by decoding the bytes as `UTF-8`.  */
  actual open fun utf8(): String = commonUtf8()

  /** Constructs a new `String` by decoding the bytes using `charset`.  */
  open fun string(charset: Charset) = String(data, charset)

  /**
   * Returns this byte string encoded as [Base64](http://www.ietf.org/rfc/rfc2045.txt). In violation
   * of the RFC, the returned string does not wrap lines at 76 columns.
   */
  actual open fun base64() = commonBase64()

  /** Returns the 128-bit MD5 hash of this byte string.  */
  open fun md5() = digest("MD5")

  /** Returns the 160-bit SHA-1 hash of this byte string.  */
  open fun sha1() = digest("SHA-1")

  /** Returns the 256-bit SHA-256 hash of this byte string.  */
  open fun sha256() = digest("SHA-256")

  /** Returns the 512-bit SHA-512 hash of this byte string.  */
  open fun sha512() = digest("SHA-512")

  internal open fun digest(algorithm: String) =
      ByteString(MessageDigest.getInstance(algorithm).digest(data))

  /** Returns the 160-bit SHA-1 HMAC of this byte string.  */
  open fun hmacSha1(key: ByteString) = hmac("HmacSHA1", key)

  /** Returns the 256-bit SHA-256 HMAC of this byte string.  */
  open fun hmacSha256(key: ByteString) = hmac("HmacSHA256", key)

  /** Returns the 512-bit SHA-512 HMAC of this byte string.  */
  open fun hmacSha512(key: ByteString) = hmac("HmacSHA512", key)

  internal open fun hmac(algorithm: String, key: ByteString): ByteString {
    try {
      val mac = Mac.getInstance(algorithm)
      mac.init(SecretKeySpec(key.toByteArray(), algorithm))
      return ByteString(mac.doFinal(data))
    } catch (e: InvalidKeyException) {
      throw IllegalArgumentException(e)
    }
  }

  /** Returns this byte string encoded as [URL-safe Base64](http://www.ietf.org/rfc/rfc4648.txt). */
  actual open fun base64Url() = commonBase64Url()

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
  @JvmOverloads
  open fun substring(beginIndex: Int = 0, endIndex: Int = size): ByteString =
      commonSubstring(beginIndex, endIndex)

  /** Returns the byte at `pos`.  */
  internal actual open fun internalGet(pos: Int) = commonGetByte(pos)

  /** Returns the byte at `index`.  */
  @JvmName("getByte")
  actual operator fun get(index: Int): Byte = internalGet(index)

  /** Returns the number of bytes in this ByteString. */
  actual val size
    @JvmName("size") get() = getSize()

  // Hack to work around Kotlin's limitation for using JvmName on open/override vals/funs
  internal actual open fun getSize() = commonGetSize()

  /** Returns a byte array containing a copy of the bytes in this `ByteString`. */
  actual open fun toByteArray() = commonToByteArray()

  /** Returns the bytes of this string without a defensive copy. Do not mutate!  */
  internal actual open fun internalArray() = commonInternalArray()

  /** Returns a `ByteBuffer` view of the bytes in this `ByteString`. */
  open fun asByteBuffer(): ByteBuffer = ByteBuffer.wrap(data).asReadOnlyBuffer()

  /** Writes the contents of this byte string to `out`.  */
  @Throws(IOException::class)
  open fun write(out: OutputStream) {
    out.write(data)
  }

  /** Writes the contents of this byte string to `buffer`.  */
  internal open fun write(buffer: Buffer) {
    buffer.write(data, 0, data.size)
  }

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

  // TODO move @JvmOverloads to common when https://youtrack.jetbrains.com/issue/KT-18882 lands

  @JvmOverloads
  actual fun indexOf(other: ByteString, fromIndex: Int) = indexOf(other.internalArray(), fromIndex)

  @JvmOverloads
  actual open fun indexOf(other: ByteArray, fromIndex: Int) = commonIndexOf(other, fromIndex)

  // TODO move lastIndexOf() when https://youtrack.jetbrains.com/issue/KT-22818 is fixed

  @JvmOverloads
  fun lastIndexOf(other: ByteString, fromIndex: Int = size) = lastIndexOf(other.internalArray(),
      fromIndex)

  @JvmOverloads
  open fun lastIndexOf(other: ByteArray, fromIndex: Int = size) = commonLastIndexOf(other, fromIndex)

  actual override fun equals(other: Any?) = commonEquals(other)

  actual override fun hashCode() = commonHashCode()

  actual override fun compareTo(other: ByteString) = commonCompareTo(other)

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  actual override fun toString() = commonToString()

  @Throws(IOException::class)
  private fun readObject(`in`: ObjectInputStream) {
    val dataLength = `in`.readInt()
    val byteString = `in`.readByteString(dataLength)
    val field = ByteString::class.java.getDeclaredField("data")
    field.isAccessible = true
    field.set(this, byteString.data)
  }

  @Throws(IOException::class)
  private fun writeObject(out: ObjectOutputStream) {
    out.writeInt(data.size)
    out.write(data)
  }

  @JvmName("-deprecated_getByte")
  @Deprecated(
      message = "moved to operator function",
      replaceWith = ReplaceWith(expression = "this[index]"),
      level = DeprecationLevel.ERROR)
  fun getByte(index: Int) = this[index]

  @JvmName("-deprecated_size")
  @Deprecated(
      message = "moved to val",
      replaceWith = ReplaceWith(expression = "size"),
      level = DeprecationLevel.ERROR)
  fun size() = size

  actual companion object {
    private const val serialVersionUID = 1L

    /** A singleton empty `ByteString`.  */
    @JvmField
    actual val EMPTY: ByteString = COMMON_EMPTY

    /** Returns a new byte string containing a clone of the bytes of `data`. */
    @JvmStatic
    actual fun of(vararg data: Byte) = commonOf(data)

    // TODO move toByteString() when https://youtrack.jetbrains.com/issue/KT-22818 is fixed

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

    /** Returns a [ByteString] containing a copy of this [ByteBuffer]. */
    @JvmStatic
    @JvmName("of")
    fun ByteBuffer.toByteString(): ByteString {
      val copy = ByteArray(remaining())
      get(copy)
      return ByteString(copy)
    }

    /** Returns a new byte string containing the `UTF-8` bytes of this [String].  */
    @JvmStatic
    actual fun String.encodeUtf8(): ByteString = commonEncodeUtf8()

    /** Returns a new [ByteString] containing the `charset`-encoded bytes of this [String].  */
    @JvmStatic
    @JvmName("encodeString")
    fun String.encode(charset: Charset = Charsets.UTF_8) = ByteString(toByteArray(charset))

    /**
     * Decodes the Base64-encoded bytes and returns their value as a byte string. Returns null if
     * this is not a Base64-encoded sequence of bytes.
     */
    @JvmStatic
    actual fun String.decodeBase64() = commonDecodeBase64()

    /** Decodes the hex-encoded bytes and returns their value a byte string.  */
    @JvmStatic
    actual fun String.decodeHex() = commonDecodeHex()

    /**
     * Reads `count` bytes from this [InputStream] and returns the result.
     *
     * @throws java.io.EOFException if `in` has fewer than `count` bytes to read.
     */
    @Throws(IOException::class)
    @JvmStatic
    @JvmName("read")
    fun InputStream.readByteString(byteCount: Int): ByteString {
      require(byteCount >= 0) { "byteCount < 0: $byteCount" }

      val result = ByteArray(byteCount)
      var offset = 0
      var read: Int
      while (offset < byteCount) {
        read = read(result, offset, byteCount - offset)
        if (read == -1) throw EOFException()
        offset += read
      }
      return ByteString(result)
    }

    @JvmName("-deprecated_decodeBase64")
    @Deprecated(
        message = "moved to extension function",
        replaceWith = ReplaceWith(
            expression = "string.decodeBase64()",
            imports = ["okio.ByteString.Companion.decodeBase64"]),
        level = DeprecationLevel.ERROR)
    fun decodeBase64(string: String) = string.decodeBase64()

    @JvmName("-deprecated_decodeHex")
    @Deprecated(
        message = "moved to extension function",
        replaceWith = ReplaceWith(
            expression = "string.decodeHex()",
            imports = ["okio.ByteString.Companion.decodeHex"]),
        level = DeprecationLevel.ERROR)
    fun decodeHex(string: String) = string.decodeHex()

    @JvmName("-deprecated_encodeString")
    @Deprecated(
        message = "moved to extension function",
        replaceWith = ReplaceWith(
            expression = "string.encode(charset)",
            imports = ["okio.ByteString.Companion.encode"]),
        level = DeprecationLevel.ERROR)
    fun encodeString(string: String, charset: Charset) = string.encode(charset)

    @JvmName("-deprecated_encodeUtf8")
    @Deprecated(
        message = "moved to extension function",
        replaceWith = ReplaceWith(
            expression = "string.encodeUtf8()",
            imports = ["okio.ByteString.Companion.encodeUtf8"]),
        level = DeprecationLevel.ERROR)
    fun encodeUtf8(string: String) = string.encodeUtf8()

    @JvmName("-deprecated_of")
    @Deprecated(
        message = "moved to extension function",
        replaceWith = ReplaceWith(
            expression = "buffer.toByteString()",
            imports = ["okio.ByteString.Companion.toByteString"]),
        level = DeprecationLevel.ERROR)
    fun of(buffer: ByteBuffer) = buffer.toByteString()

    @JvmName("-deprecated_of")
    @Deprecated(
        message = "moved to extension function",
        replaceWith = ReplaceWith(
            expression = "array.toByteString(offset, byteCount)",
            imports = ["okio.ByteString.Companion.toByteString"]),
        level = DeprecationLevel.ERROR)
    fun of(array: ByteArray, offset: Int, byteCount: Int) = array.toByteString(offset, byteCount)

    @JvmName("-deprecated_read")
    @Deprecated(
        message = "moved to extension function",
        replaceWith = ReplaceWith(
            expression = "inputstream.readByteString(byteCount)",
            imports = ["okio.ByteString.Companion.readByteString"]),
        level = DeprecationLevel.ERROR)
    fun read(inputstream: InputStream, byteCount: Int) = inputstream.readByteString(byteCount)
  }
}
