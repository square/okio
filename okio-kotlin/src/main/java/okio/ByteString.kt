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

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.text.Charsets.UTF_8

/**
 * Returns a [ByteString] containing a copy of this [ByteArray].
 *
 * @see ByteString.of
 */
inline fun ByteArray.toByteString(offset: Int = 0, byteCount: Int = size): ByteString =
    ByteString.of(this, offset, byteCount)

/**
 * Returns a [ByteString] containing a copy of this [ByteBuffer].
 *
 * @see ByteString.of
 */
inline fun ByteBuffer.toByteString(): ByteString = ByteString.of(this)

/**
 * Returns a [ByteString] containing the [charset]-encoded bytes of this [String].
 *
 * @see ByteString.encodeString
 */
inline fun String.encodeByteString(charset: Charset = UTF_8): ByteString =
    ByteString.encodeString(this, charset)

/**
 * Returns a [ByteString] decoded from this base64 [String].
 *
 * @see ByteString.decodeBase64
 */
inline fun String.decodeBase64(): ByteString? = ByteString.decodeBase64(this)

/**
 * Returns a [ByteString] decoded from this hex [String].
 *
 * @see ByteString.decodeHex
 */
inline fun String.decodeHex(): ByteString = ByteString.decodeHex(this)

/**
 * Returns a [ByteString] contains [byteCount] bytes read from this [InputStream].
 *
 * @see ByteString.read
 */
inline fun InputStream.readByteString(byteCount: Int): ByteString =
    ByteString.read(this, byteCount)
