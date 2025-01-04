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

import okio.internal.HashFunction
import okio.internal.Hmac
import okio.internal.Md5
import okio.internal.Sha1
import okio.internal.Sha256
import okio.internal.Sha512

actual class HashingSink internal constructor(
  private val sink: Sink,
  private val hashFunction: HashFunction,
) : Sink {

  actual override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)

    // Hash byteCount bytes from the prefix of source.
    var hashedCount = 0L
    var s = source.head!!
    while (hashedCount < byteCount) {
      val toHash = minOf(byteCount - hashedCount, s.limit - s.pos).toInt()
      hashFunction.update(s.data, s.pos, toHash)
      hashedCount += toHash
      s = s.next!!
    }

    // Write those bytes to the sink.
    sink.write(source, byteCount)
  }

  actual override fun flush() = sink.flush()

  actual override fun timeout(): Timeout = sink.timeout()

  actual override fun close() = sink.close()

  /**
   * Returns the hash of the bytes accepted thus far and resets the internal state of this sink.
   *
   * **Warning:** This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes accepted.
   */
  actual val hash: ByteString
    get() {
      val result = hashFunction.digest()
      return ByteString(result)
    }

  actual companion object {

    /** Returns a sink that uses the obsolete MD5 hash algorithm to produce 128-bit hashes. */
    actual fun md5(sink: Sink) = HashingSink(sink, Md5())

    /** Returns a sink that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes. */
    actual fun sha1(sink: Sink) = HashingSink(sink, Sha1())

    /** Returns a sink that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
    actual fun sha256(sink: Sink) = HashingSink(sink, Sha256())

    /** Returns a sink that uses the SHA-512 hash algorithm to produce 512-bit hashes. */
    actual fun sha512(sink: Sink) = HashingSink(sink, Sha512())

    /** Returns a sink that uses the obsolete SHA-1 HMAC algorithm to produce 160-bit hashes. */
    actual fun hmacSha1(sink: Sink, key: ByteString) = HashingSink(sink, Hmac.sha1(key))

    /** Returns a sink that uses the SHA-256 HMAC algorithm to produce 256-bit hashes. */
    actual fun hmacSha256(sink: Sink, key: ByteString) = HashingSink(sink, Hmac.sha256(key))

    /** Returns a sink that uses the SHA-512 HMAC algorithm to produce 512-bit hashes. */
    actual fun hmacSha512(sink: Sink, key: ByteString) = HashingSink(sink, Hmac.sha512(key))
  }
}
