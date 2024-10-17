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
expect class HashingSource : Source {

  /**
   * Returns the hash of the bytes supplied thus far and resets the internal state of this source.
   *
   * **Warning:** This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes supplied.
   */
  val hash: ByteString

  override fun close()
  override fun read(sink: Buffer, byteCount: Long): Long
  override fun timeout(): Timeout

  companion object {
    /**
     * Returns a source that uses the obsolete MD5 hash algorithm to produce 128-bit hashes.
     *
     * MD5 has been vulnerable to collisions since 2004. It should not be used in new code.
     */
    fun md5(source: Source): HashingSource

    /**
     * Returns a source that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes.
     *
     * SHA-1 has been vulnerable to collisions since 2017. It should not be used in new code.
     */
    fun sha1(source: Source): HashingSource

    /** Returns a source that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
    fun sha256(source: Source): HashingSource

    /** Returns a source that uses the SHA-512 hash algorithm to produce 512-bit hashes. */
    fun sha512(source: Source): HashingSource

    /** Returns a source that uses the obsolete SHA-1 HMAC algorithm to produce 160-bit hashes. */
    fun hmacSha1(source: Source, key: ByteString): HashingSource

    /** Returns a source that uses the SHA-256 HMAC algorithm to produce 256-bit hashes. */
    fun hmacSha256(source: Source, key: ByteString): HashingSource

    /** Returns a source that uses the SHA-512 HMAC algorithm to produce 512-bit hashes. */
    fun hmacSha512(source: Source, key: ByteString): HashingSource
  }
}
