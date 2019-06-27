/*
 * Copyright (C) 2019 Square, Inc.
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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.value
import openssl.EVP_DigestFinal_ex
import openssl.EVP_DigestInit_ex
import openssl.EVP_DigestSignFinal
import openssl.EVP_DigestSignInit
import openssl.EVP_DigestUpdate
import openssl.EVP_MAX_MD_SIZE
import openssl.EVP_MD
import openssl.EVP_MD_CTX
import openssl.EVP_MD_CTX_free
import openssl.EVP_MD_CTX_new
import openssl.EVP_MD_CTX_reset
import openssl.EVP_PKEY
import openssl.EVP_PKEY_HMAC
import openssl.EVP_PKEY_new_mac_key
import openssl.EVP_md5
import openssl.EVP_sha1
import openssl.EVP_sha256
import openssl.EVP_sha512

class HashingSink private constructor(
  private val delegate: Sink,
  private val algorithm: CPointer<EVP_MD>,
  key: ByteString? = null
) : Sink {

  private val key: CPointer<EVP_PKEY>? = key?.let {
    require(it.size > 0) { "empty key" }
    EVP_PKEY_new_mac_key(EVP_PKEY_HMAC, null, it.data.toUByteArray().refTo(0), it.size)!!
  }

  private val ctx: CPointer<EVP_MD_CTX> = EVP_MD_CTX_new()?.also { ctx ->
    EVP_DigestInit_ex(ctx, algorithm, null).checkEvpError()
    if (key != null) {
      EVP_DigestSignInit(ctx, null, algorithm, null, this.key).checkEvpError()
    }
  }!!

  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)

    // Hash byteCount bytes from the prefix of source.
    var hashedCount = 0L
    var s = source.head!!
    while (hashedCount < byteCount) {
      val toHash = minOf(byteCount - hashedCount, s.limit - s.pos).toInt()
      EVP_DigestUpdate(ctx, s.data.refTo(s.pos), toHash.toULong()).checkEvpError()
      hashedCount += toHash
      s = s.next!!
    }

    // Write those bytes to the sink.
    delegate.write(source, byteCount)
  }

  override fun flush() {
    delegate.flush()
  }

  override fun timeout(): Timeout {
    return delegate.timeout()
  }

  override fun close() {
    EVP_MD_CTX_free(ctx)
    delegate.close()
  }

  /**
   * Returns the hash of the bytes accepted thus far and resets the internal state of this sink.
   *
   * **Warning:** This method is not idempotent. Each time this method is called its
   * internal state is cleared. This starts a new hash with zero bytes accepted.
   */
  val hash: ByteString
    get() {
      return memScoped {
        val data = ByteArray(EVP_MAX_MD_SIZE)

        val size: Int
        size = if (key != null) {
          val length: ULongVar = alloc()
          EVP_DigestSignFinal(ctx, data.asUByteArray().refTo(0), length.ptr).checkEvpError()
          length.value.toInt()
        } else {
          val length: UIntVar = alloc()
          EVP_DigestFinal_ex(ctx, data.asUByteArray().refTo(0), length.ptr).checkEvpError()
          length.value.toInt()
        }

        EVP_MD_CTX_reset(ctx)
        EVP_DigestInit_ex(ctx, algorithm, null).checkEvpError()
        if (key != null) {
          EVP_DigestSignInit(ctx, null, algorithm, null, key).checkEvpError()
        }

        if (size != EVP_MAX_MD_SIZE) ByteString(data.copyOfRange(0, size))
        else ByteString(data)
      }
    }

  companion object {
    /** Returns a sink that uses the obsolete MD5 hash algorithm to produce 128-bit hashes. */
    fun md5(sink: Sink) = HashingSink(sink, EVP_md5()!!)

    /** Returns a sink that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes. */
    fun sha1(sink: Sink) = HashingSink(sink, EVP_sha1()!!)

    /** Returns a sink that uses the SHA-256 hash algorithm to produce 256-bit hashes. */
    fun sha256(sink: Sink) = HashingSink(sink, EVP_sha256()!!)

    /** Returns a sink that uses the SHA-512 hash algorithm to produce 512-bit hashes. */
    fun sha512(sink: Sink) = HashingSink(sink, EVP_sha512()!!)

    /** Returns a sink that uses the obsolete SHA-1 HMAC algorithm to produce 160-bit hashes. */
    fun hmacSha1(sink: Sink, key: ByteString) = HashingSink(sink, EVP_sha1()!!, key)

    /** Returns a sink that uses the SHA-256 HMAC algorithm to produce 256-bit hashes. */
    fun hmacSha256(sink: Sink, key: ByteString) = HashingSink(sink, EVP_sha256()!!, key)

    /** Returns a sink that uses the SHA-512 HMAC algorithm to produce 512-bit hashes. */
    fun hmacSha512(sink: Sink, key: ByteString) = HashingSink(sink, EVP_sha512()!!, key)
  }
}
