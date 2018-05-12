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

import okio.common.COMMON_EMPTY
import okio.common.COMMON_HEX_DIGITS
import okio.common.commonCompareTo
import okio.common.commonDecodeHex
import okio.common.commonEndsWith
import okio.common.commonEquals
import okio.common.commonGetByte
import okio.common.commonGetSize
import okio.common.commonHashCode
import okio.common.commonInternalArray
import okio.common.commonOf
import okio.common.commonRangeEquals
import okio.common.commonStartsWith
import okio.common.commonSubstring
import okio.common.commonToAsciiLowercase
import okio.common.commonToAsciiUppercase
import okio.common.commonToByteArray
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
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.jvm.Transient

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
  actual open fun utf8(): String {
    var result = utf8
    if (result == null) {
      // We don't care if we double-allocate in racy code.
      result = String(data, Charsets.UTF_8)
      utf8 = result
    }
    return result
  }

  /** Constructs a new `String` by decoding the bytes using `charset`.  */
  open fun string(charset: Charset) = String(data, charset)

  /**
   * Returns this byte string encoded as [Base64](http://www.ietf.org/rfc/rfc2045.txt). In violation
   * of the RFC, the returned string does not wrap lines at 76 columns.
   */
  actual open fun base64() = data.encodeBase64()

  /** Returns the 128-bit MD5 hash of this byte string.  */
  open fun md5() = digest("MD5")

  /** Returns the 160-bit SHA-1 hash of this byte string.  */
  open fun sha1() = digest("SHA-1")

  /** Returns the 256-bit SHA-256 hash of this byte string.  */
  open fun sha256() = digest("SHA-256")

  /** Returns the 512-bit SHA-512 hash of this byte string.  */
  open fun sha512() = digest("SHA-512")

  private fun digest(algorithm: String): ByteString {
    try {
      return ByteString.of(*MessageDigest.getInstance(algorithm).digest(data))
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
    }
  }

  /** Returns the 160-bit SHA-1 HMAC of this byte string.  */
  open fun hmacSha1(key: ByteString) = hmac("HmacSHA1", key)

  /** Returns the 256-bit SHA-256 HMAC of this byte string.  */
  open fun hmacSha256(key: ByteString) = hmac("HmacSHA256", key)

  /** Returns the 512-bit SHA-512 HMAC of this byte string.  */
  open fun hmacSha512(key: ByteString) = hmac("HmacSHA512", key)

  private fun hmac(algorithm: String, key: ByteString): ByteString {
    try {
      val mac = Mac.getInstance(algorithm)
      mac.init(SecretKeySpec(key.toByteArray(), algorithm))
      return ByteString.of(*mac.doFinal(data))
    } catch (e: NoSuchAlgorithmException) {
      throw AssertionError(e)
    } catch (e: InvalidKeyException) {
      throw IllegalArgumentException(e)
    }
  }

  /** Returns this byte string encoded as [URL-safe Base64](http://www.ietf.org/rfc/rfc4648.txt). */
  actual open fun base64Url() = data.encodeBase64(map = BASE64_URL_SAFE)

  /** Returns this byte string encoded in hexadecimal.  */
  actual open fun hex(): String {
    val result = CharArray(data.size * 2)
    var c = 0
    for (b in data) {
      result[c++] = HEX_DIGITS[b shr 4 and 0xf]
      result[c++] = HEX_DIGITS[b       and 0xf]
    }
    return String(result)
  }

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
  actual override fun toString(): String {
    if (data.isEmpty()) return "[size=0]"

    val text = utf8()
    val i = codePointIndexToCharIndex(text, 64)

    if (i == -1) {
      return if (data.size <= 64) {
        "[hex=${hex()}]"
      } else {
        "[size=${data.size} hex=${substring(0, 64).hex()}…]"
      }
    }

    val safeText = text.substring(0, i)
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    return if (i < text.length) {
      "[size=${data.size} text=$safeText…]"
    } else {
      "[text=$safeText]"
    }
  }

  @Throws(IOException::class)
  private fun readObject(`in`: ObjectInputStream) {
    val dataLength = `in`.readInt()
    val byteString = `in`.readByteString(dataLength)
    try {
      val field = ByteString::class.java.getDeclaredField("data")
      field.isAccessible = true
      field.set(this, byteString.data)
    } catch (e: NoSuchFieldException) {
      throw AssertionError()
    } catch (e: IllegalAccessException) {
      throw AssertionError()
    }
  }

  @Throws(IOException::class)
  private fun writeObject(out: ObjectOutputStream) {
    out.writeInt(data.size)
    out.write(data)
  }

  actual companion object {
    internal actual val HEX_DIGITS = COMMON_HEX_DIGITS
    private const val serialVersionUID = 1L

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
    actual fun String.encodeUtf8(): ByteString {
      val byteString = ByteString(toByteArray(Charsets.UTF_8))
      byteString.utf8 = this
      return byteString
    }

    /** Returns a new [ByteString] containing the `charset`-encoded bytes of this [String].  */
    @JvmStatic
    @JvmName("encodeString")
    fun String.encode(charset: Charset = Charsets.UTF_8) = ByteString(toByteArray(charset))

    /**
     * Decodes the Base64-encoded bytes and returns their value as a byte string. Returns null if
     * this is not a Base64-encoded sequence of bytes.
     */
    @JvmStatic
    actual fun String.decodeBase64(): ByteString? {
      val decoded = decodeBase64ToArray()
      return if (decoded != null) ByteString(decoded) else null
    }

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

    internal fun codePointIndexToCharIndex(s: String, codePointCount: Int): Int {
      var i = 0
      var j = 0
      val length = s.length
      var c: Int
      while (i < length) {
        if (j == codePointCount) {
          return i
        }
        c = s.codePointAt(i)
        if ((Character.isISOControl(c) && c != '\n'.toInt() && c != '\r'.toInt())
            || c == Buffer.REPLACEMENT_CHARACTER) {
          return -1
        }
        j++
        i += Character.charCount(c)
      }
      return s.length
    }
  }
}
