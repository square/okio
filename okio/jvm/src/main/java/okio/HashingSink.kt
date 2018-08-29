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
 * A sink that computes a hash of the full stream of bytes it has accepted. To use, create an
 * instance with your preferred hash algorithm. Write all of the data to the sink and then call
 * [hash] to compute the final hash value.
 *
 * In this example we use `HashingSink` with a [BufferedSink] to make writing to the
 * sink easier.
 * ```
 * HashingSink hashingSink = HashingSink.sha256(s);
 * BufferedSink bufferedSink = Okio.buffer(hashingSink);
 *
 * ... // Write to bufferedSink and either flush or close it.
 *
 * ByteString hash = hashingSink.hash();
 * ```
 */
class HashingSink : ForwardingSink {
  private val messageDigest: MessageDigest?
  private val mac: Mac?

  internal constructor(sink: Sink, algorithm: String) : super(sink) {
    this.messageDigest = MessageDigest.getInstance(algorithm)
    this.mac = null
  }

  internal constructor(sink: Sink, key: ByteString, algorithm: String) : super(sink) {
    try {
      this.mac = Mac.getInstance(algorithm).apply {
        init(SecretKeySpec(key.toByteArray(), algorithm))
      }
      this.messageDigest = null
    } catch (e: InvalidKeyException) {
      throw IllegalArgumentException(e)
    }
  }

  @Throws(IOException::class)
  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)

    // Hash byteCount bytes from the prefix of source.
    var hashedCount = 0L
    var s = source.head!!
    while (hashedCount < byteCount) {
      val toHash = minOf(byteCount - hashedCount, s.limit - s.pos).toInt()
      if (messageDigest != null) {
        messageDigest.update(s.data, s.pos, toHash)
      } else {
        mac!!.update(s.data, s.pos, toHash)
      }
      hashedCount += toHash
      s = s.next!!
    }

    // Write those bytes to the sink.
    super.write(source, byteCount)
  }

  /**
   * Returns the hash of the bytes accepted thus far and resets the internal state of this sink.
   *
   * **Warning:** This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes accepted.
   */
  @get:JvmName("hash")
  val hash: ByteString
    get() {
      val result = if (messageDigest != null) messageDigest.digest() else mac!!.doFinal()
      return ByteString(result)
    }

  @JvmName("-deprecated_hash")
  @Deprecated(
      message = "moved to val",
      replaceWith = ReplaceWith(expression = "hash"),
      level = DeprecationLevel.ERROR)
  fun hash() = hash

  companion object {
    /** Returns a sink that uses the obsolete MD5 hash algorithm to produce 128-bit hashes. */
    @JvmStatic fun md5(sink: Sink) = HashingSink(sink, "MD5")

    /** Returns a sink that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes. */
    @JvmStatic fun sha1(sink: Sink) = HashingSink(sink, "SHA-1")

    /** Returns a sink that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
    @JvmStatic fun sha256(sink: Sink) = HashingSink(sink, "SHA-256")

    /** Returns a sink that uses the SHA-512 hash algorithm to produce 512-bit hashes. */
    @JvmStatic fun sha512(sink: Sink) = HashingSink(sink, "SHA-512")

    /** Returns a sink that uses the obsolete SHA-1 HMAC algorithm to produce 160-bit hashes. */
    @JvmStatic fun hmacSha1(sink: Sink, key: ByteString) = HashingSink(sink, key, "HmacSHA1")

    /** Returns a sink that uses the SHA-256 HMAC algorithm to produce 256-bit hashes. */
    @JvmStatic fun hmacSha256(sink: Sink, key: ByteString) = HashingSink(sink, key, "HmacSHA256")

    /** Returns a sink that uses the SHA-512 HMAC algorithm to produce 512-bit hashes. */
    @JvmStatic fun hmacSha512(sink: Sink, key: ByteString) = HashingSink(sink, key, "HmacSHA512")
  }
}
