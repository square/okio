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
 * Returns a [HashingSource] that uses the obsolete MD5 hash algorithm to produce 128-bit hashes.
 *
 * @see HashingSource.md5
 */
inline fun Source.hashMd5(): HashingSource = HashingSource.md5(this)

/**
 * Returns a [HashingSource] that uses the obsolete SHA-1 hash algorithm to produce 160-bit hashes.
 *
 * @see HashingSource.sha1
 */
inline fun Source.hashSha1(): HashingSource = HashingSource.sha1(this)

/**
 * Returns a [HashingSource] that uses the SHA-256 hash algorithm to produce 256-bit hashes.
 *
 * @see HashingSource.sha256
 */
inline fun Source.hashSha256(): HashingSource = HashingSource.sha256(this)

/**
 * Returns a [HashingSource] that uses the SHA-512 hash algorithm to produce 512-bit hashes.
 *
 * @see HashingSource.sha512
 */
inline fun Source.hashSha512(): HashingSource = HashingSource.sha512(this)

/**
 * Returns a [HashingSource] that uses the obsolete SHA-1 hash algorithm with [key] to produce
 * 160-bit hashes.
 *
 * @see HashingSource.hmacSha1
 */
inline fun Source.hashHmacSha1(key: ByteString): HashingSource =
    HashingSource.hmacSha1(this, key)

/**
 * Returns a [HashingSource] that uses the SHA-256 hash algorithm with [key] to produce 256-bit
 * hashes.
 *
 * @see HashingSource.hmacSha256
 */
inline fun Source.hashHmacSha256(key: ByteString): HashingSource =
    HashingSource.hmacSha256(this, key)

/**
 * Returns a [HashingSource] that uses the SHA-512 hash algorithm with [key] to produce 512-bit
 * hashes.
 *
 * @see HashingSource.hmacSha512
 */
inline fun Source.hashHmacSha512(key: ByteString): HashingSource =
    HashingSource.hmacSha512(this, key)
