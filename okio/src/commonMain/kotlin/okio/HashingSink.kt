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

/**
 * A sink that computes a hash of the full stream of bytes it has accepted. To use, create an
 * instance with your preferred hash algorithm. Write all of the data to the sink and then call
 * [hash] to compute the final hash value.
 *
 * In this example we use `HashingSink` with a [BufferedSink] to make writing to the
 * sink easier.
 *
 * ```java
 * HashingSink hashingSink = HashingSink.sha256(s);
 * BufferedSink bufferedSink = Okio.buffer(hashingSink);
 *
 * ... // Write to bufferedSink and either flush or close it.
 *
 * ByteString hash = hashingSink.hash();
 * ```
 */
expect class HashingSink : Sink {

  /**
   * Returns the hash of the bytes accepted thus far and resets the internal state of this sink.
   *
   * **Warning:** This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes accepted.
   */
  val hash: ByteString

  override fun close()
  override fun flush()
  override fun timeout(): Timeout
  override fun write(source: Buffer, byteCount: Long)

  companion object {
    /**
     * Returns a sink that uses the obsolete MD5 hash algorithm to produce 128-bit hashes.
     *
     * MD5 has been vulnerable to collisions since 2004. It should not be used in new code.
     */
    fun md5(sink: Sink): HashingSink

    /**
     * Returns a sink that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes.
     *
     * SHA-1 has been vulnerable to collisions since 2017. It should not be used in new code.
     */
    fun sha1(sink: Sink): HashingSink

    /** Returns a sink that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
    fun sha256(sink: Sink): HashingSink

    /** Returns a sink that uses the SHA-512 hash algorithm to produce 512-bit hashes. */
    fun sha512(sink: Sink): HashingSink

    /** Returns a sink that uses the obsolete SHA-1 HMAC algorithm to produce 160-bit hashes. */
    fun hmacSha1(sink: Sink, key: ByteString): HashingSink

    /** Returns a sink that uses the SHA-256 HMAC algorithm to produce 256-bit hashes. */
    fun hmacSha256(sink: Sink, key: ByteString): HashingSink

    /** Returns a sink that uses the SHA-512 HMAC algorithm to produce 512-bit hashes. */
    fun hmacSha512(sink: Sink, key: ByteString): HashingSink
  }
}
