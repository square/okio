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

actual class HashingSource internal constructor(
  private val source: Source,
  private val hashFunction: HashFunction,
) : Source {

  actual override fun read(sink: Buffer, byteCount: Long): Long {
    val result = source.read(sink, byteCount)

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
        hashFunction.update(s.data, pos, s.limit - pos)
        offset += s.limit - s.pos
        start = offset
        s = s.next!!
      }
    }

    return result
  }

  actual override fun timeout(): Timeout =
    source.timeout()

  actual override fun close() =
    source.close()

  actual val hash: ByteString
    get() {
      val result = hashFunction.digest()
      return ByteString(result)
    }

  actual companion object {

    /** Returns a source that uses the obsolete MD5 hash algorithm to produce 128-bit hashes. */
    actual fun md5(source: Source) = HashingSource(source, Md5())

    /** Returns a source that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes. */
    actual fun sha1(source: Source) = HashingSource(source, Sha1())

    /** Returns a source that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
    actual fun sha256(source: Source) = HashingSource(source, Sha256())

    /** Returns a source that uses the SHA-512 hash algorithm to produce 512-bit hashes. */
    actual fun sha512(source: Source) = HashingSource(source, Sha512())

    /** Returns a source that uses the obsolete SHA-1 HMAC algorithm to produce 160-bit hashes. */
    actual fun hmacSha1(source: Source, key: ByteString) = HashingSource(source, Hmac.sha1(key))

    /** Returns a source that uses the SHA-256 HMAC algorithm to produce 256-bit hashes. */
    actual fun hmacSha256(source: Source, key: ByteString) = HashingSource(source, Hmac.sha256(key))

    /** Returns a source that uses the SHA-512 HMAC algorithm to produce 512-bit hashes. */
    actual fun hmacSha512(source: Source, key: ByteString) = HashingSource(source, Hmac.sha512(key))
  }
}
