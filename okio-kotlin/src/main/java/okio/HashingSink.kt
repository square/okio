/*
 * Copyright (C) 2018 Square, Inc.
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

@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package okio

/**
 * Returns a [HashingSink] that uses the obsolete MD5 hash algorithm to produce 128-bit hashes.
 *
 * @see HashingSink.md5
 */
inline fun Sink.hashMd5(): HashingSink = HashingSink.md5(this)

/**
 * Returns a [HashingSink] that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes.
 *
 * @see HashingSink.sha1
 */
inline fun Sink.hashSha1(): HashingSink = HashingSink.sha1(this)

/**
 * Returns a [HashingSink] that uses the SHA-256 algorithm to produce 256-bit hashes.
 *
 * @see HashingSink.sha256
 */
inline fun Sink.hashSha256(): HashingSink = HashingSink.sha256(this)

/**
 * Returns a [HashingSink] that uses the SHA-512 algorithm to produce 512-bit hashes.
 *
 * @see HashingSink.sha512
 */
inline fun Sink.hashSha512(): HashingSink = HashingSink.sha512(this)

/**
 * Returns a [HashingSink] that uses the obsolete SHA-1 HMAC algorithm with [key] to produce 160-bit
 * hashes.
 *
 * @see HashingSink.hmacSha1
 */
inline fun Sink.hashHmacSha1(key: ByteString): HashingSink = HashingSink.hmacSha1(this, key)

/**
 * Returns a [HashingSink] that uses the SHA-256 HMAC algorithm with [key] to produce 256-bit
 * hashes.
 *
 * @see HashingSink.hmacSha256
 */
inline fun Sink.hashHmacSha256(key: ByteString): HashingSink = HashingSink.hmacSha256(this, key)

/**
 * Returns a [HashingSink] that uses the SHA-512 HMAC algorithm with [key] to produce 512-bit
 * hashes.
 *
 * @see HashingSink.hmacSha512
 */
inline fun Sink.hashHmacSha512(key: ByteString): HashingSink = HashingSink.hmacSha512(this, key)
