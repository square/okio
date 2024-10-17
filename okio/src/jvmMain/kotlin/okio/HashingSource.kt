/*
 * Copyright (C) 2016 Square, Inc.
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

import java.io.IOException
import java.security.InvalidKeyException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A source that computes a hash of the full stream of bytes it has supplied. To use, create an
 * instance with your preferred hash algorithm. Exhaust the source by reading all of its bytes and
 * then call [hash] to compute the final hash value.
 *
 *
 * In this example we use `HashingSource` with a [BufferedSource] to make reading
 * from the source easier.
 *
 * ```java
 * HashingSource hashingSource = HashingSource.sha256(rawSource);
 * BufferedSource bufferedSource = Okio.buffer(hashingSource);
 *
 * ... // Read all of bufferedSource.
 *
 * ByteString hash = hashingSource.hash();
 * ```
 */
actual class HashingSource : ForwardingSource, Source { // Need to explicitly declare source pending fix for https://youtrack.jetbrains.com/issue/KT-20641
  private val messageDigest: MessageDigest?
  private val mac: Mac?

  internal constructor(source: Source, digest: MessageDigest) : super(source) {
    this.messageDigest = digest
    this.mac = null
  }

  internal constructor(source: Source, algorithm: String) : this(source, MessageDigest.getInstance(algorithm))

  internal constructor(source: Source, mac: Mac) : super(source) {
    this.mac = mac
    this.messageDigest = null
  }

  internal constructor(source: Source, key: ByteString, algorithm: String) : this(
    source,
    try {
      Mac.getInstance(algorithm).apply {
        init(SecretKeySpec(key.toByteArray(), algorithm))
      }
    } catch (e: InvalidKeyException) {
      throw IllegalArgumentException(e)
    },
  )

  @Throws(IOException::class)
  actual override fun read(sink: Buffer, byteCount: Long): Long {
    val result = super.read(sink, byteCount)

    if (result != -1L) {
      var start = sink.size - result

      // Find the first segment that has new bytes.
      var offset = sink.size
      var s = sink.head!!
      while (offset > start) {
        s = s.prev!!
        offset -= (s.limit - s.pos).toLong()
      }

      // Hash that segment and all the rest until the end.
      while (offset < sink.size) {
        val pos = (s.pos + start - offset).toInt()
        if (messageDigest != null) {
          messageDigest.update(s.data, pos, s.limit - pos)
        } else {
          mac!!.update(s.data, pos, s.limit - pos)
        }
        offset += s.limit - s.pos
        start = offset
        s = s.next!!
      }
    }

    return result
  }

  /**
   * Returns the hash of the bytes supplied thus far and resets the internal state of this source.
   *
   * **Warning:** This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes supplied.
   */
  @get:JvmName("hash")
  actual val hash: ByteString
    get() {
      val result = if (messageDigest != null) messageDigest.digest() else mac!!.doFinal()
      return ByteString(result)
    }

  @JvmName("-deprecated_hash")
  @Deprecated(
    message = "moved to val",
    replaceWith = ReplaceWith(expression = "hash"),
    level = DeprecationLevel.ERROR,
  )
  fun hash() = hash

  actual companion object {
    /**
     * Returns a source that uses the obsolete MD5 hash algorithm to produce 128-bit hashes.
     *
     * MD5 has been vulnerable to collisions since 2004. It should not be used in new code.
     */
    @JvmStatic
    actual fun md5(source: Source) = HashingSource(source, "MD5")

    /**
     * Returns a source that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes.
     *
     * SHA-1 has been vulnerable to collisions since 2017. It should not be used in new code.
     */
    @JvmStatic
    actual fun sha1(source: Source) = HashingSource(source, "SHA-1")

    /** Returns a source that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
    @JvmStatic
    actual fun sha256(source: Source) = HashingSource(source, "SHA-256")

    /** Returns a source that uses the SHA-512 hash algorithm to produce 512-bit hashes. */
    @JvmStatic
    actual fun sha512(source: Source) = HashingSource(source, "SHA-512")

    /** Returns a source that uses the obsolete SHA-1 HMAC algorithm to produce 160-bit hashes. */
    @JvmStatic
    actual fun hmacSha1(source: Source, key: ByteString) = HashingSource(source, key, "HmacSHA1")

    /** Returns a source that uses the SHA-256 HMAC algorithm to produce 256-bit hashes. */
    @JvmStatic
    actual fun hmacSha256(source: Source, key: ByteString) = HashingSource(source, key, "HmacSHA256")

    /** Returns a source that uses the SHA-512 HMAC algorithm to produce 512-bit hashes. */
    @JvmStatic
    actual fun hmacSha512(source: Source, key: ByteString) = HashingSource(source, key, "HmacSHA512")
  }
}
