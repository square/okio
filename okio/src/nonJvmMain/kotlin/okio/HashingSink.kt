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

import okio.internal.HMac

actual class HashingSink private constructor(
  private val sink: Sink,
  private val mac: HMac
) : Sink {

  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)

    // Hash byteCount bytes from the prefix of source.
    var hashedCount = 0L
    var s = source.head!!
    while (hashedCount < byteCount) {
      val toHash = minOf(byteCount - hashedCount, s.limit - s.pos).toInt()
      mac!!.update(s.data, s.pos, toHash)
      hashedCount += toHash
      s = s.next!!
    }

    // Write those bytes to the sink.
    sink.write(source, byteCount)
  }

  override fun flush() = sink.flush()

  override fun timeout(): Timeout = sink.timeout()

  override fun close() = sink.close()

  /**
   * Returns the hash of the bytes accepted thus far and resets the internal state of this sink.
   *
   * **Warning:** This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes accepted.
   */
  actual val hash: ByteString
    get() = ByteString(mac.doFinal())

  actual companion object {
    /** Returns a sink that uses the obsolete SHA-1 HMAC algorithm to produce 160-bit hashes. */
    actual fun hmacSha1(sink: Sink, key: ByteString) = HashingSink(sink, HMac.sha1(key.toByteArray()))

    /** Returns a sink that uses the SHA-256 HMAC algorithm to produce 256-bit hashes. */
    actual fun hmacSha256(sink: Sink, key: ByteString) = HashingSink(sink, HMac.sha256(key.toByteArray()))

    /** Returns a sink that uses the SHA-512 HMAC algorithm to produce 512-bit hashes. */
    actual fun hmacSha512(sink: Sink, key: ByteString) = HashingSink(sink, HMac.sha512(key.toByteArray()))
  }
}
