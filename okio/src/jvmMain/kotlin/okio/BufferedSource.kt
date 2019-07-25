/*
 * Copyright (C) 2014 Square, Inc.
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
import java.io.InputStream
import java.nio.channels.ReadableByteChannel
import java.nio.charset.Charset

actual interface BufferedSource : Source, ReadableByteChannel {
  /** Returns this source's internal buffer. */
  @Deprecated(
    message = "moved to val: use getBuffer() instead",
    replaceWith = ReplaceWith(expression = "buffer"),
    level = DeprecationLevel.WARNING)
  fun buffer(): Buffer

  actual val buffer: Buffer

  @Throws(IOException::class)
  actual fun exhausted(): Boolean

  @Throws(IOException::class)
  actual fun require(byteCount: Long)

  @Throws(IOException::class)
  actual fun request(byteCount: Long): Boolean

  @Throws(IOException::class)
  actual fun readByte(): Byte

  @Throws(IOException::class)
  actual fun readShort(): Short

  @Throws(IOException::class)
  actual fun readShortLe(): Short

  @Throws(IOException::class)
  actual fun readInt(): Int

  @Throws(IOException::class)
  actual fun readIntLe(): Int

  @Throws(IOException::class)
  actual fun readLong(): Long

  @Throws(IOException::class)
  actual fun readLongLe(): Long

  @Throws(IOException::class)
  actual fun readDecimalLong(): Long

  @Throws(IOException::class)
  actual fun readHexadecimalUnsignedLong(): Long

  @Throws(IOException::class)
  actual fun skip(byteCount: Long)

  @Throws(IOException::class)
  actual fun readByteString(): ByteString

  @Throws(IOException::class)
  actual fun readByteString(byteCount: Long): ByteString

  @Throws(IOException::class)
  actual fun select(options: Options): Int

  @Throws(IOException::class)
  actual fun readByteArray(): ByteArray

  @Throws(IOException::class)
  actual fun readByteArray(byteCount: Long): ByteArray

  @Throws(IOException::class)
  actual fun read(sink: ByteArray): Int

  @Throws(IOException::class)
  actual fun readFully(sink: ByteArray)

  @Throws(IOException::class)
  actual fun read(sink: ByteArray, offset: Int, byteCount: Int): Int

  @Throws(IOException::class)
  actual fun readFully(sink: Buffer, byteCount: Long)

  @Throws(IOException::class)
  actual fun readAll(sink: Sink): Long

  @Throws(IOException::class)
  actual fun readUtf8(): String

  @Throws(IOException::class)
  actual fun readUtf8(byteCount: Long): String

  @Throws(IOException::class)
  actual fun readUtf8Line(): String?

  @Throws(IOException::class)
  actual fun readUtf8LineStrict(): String

  @Throws(IOException::class)
  actual fun readUtf8LineStrict(limit: Long): String

  @Throws(IOException::class)
  actual fun readUtf8CodePoint(): Int

  /** Removes all bytes from this, decodes them as `charset`, and returns the string. */
  @Throws(IOException::class)
  fun readString(charset: Charset): String

  /**
   * Removes `byteCount` bytes from this, decodes them as `charset`, and returns the
   * string.
   */
  @Throws(IOException::class)
  fun readString(byteCount: Long, charset: Charset): String

  @Throws(IOException::class)
  actual fun indexOf(b: Byte): Long

  @Throws(IOException::class)
  actual fun indexOf(b: Byte, fromIndex: Long): Long

  @Throws(IOException::class)
  actual fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long

  @Throws(IOException::class)
  actual fun indexOf(bytes: ByteString): Long

  @Throws(IOException::class)
  actual fun indexOf(bytes: ByteString, fromIndex: Long): Long

  @Throws(IOException::class)
  actual fun indexOfElement(targetBytes: ByteString): Long

  @Throws(IOException::class)
  actual fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long

  @Throws(IOException::class)
  actual fun rangeEquals(offset: Long, bytes: ByteString): Boolean

  @Throws(IOException::class)
  actual fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean

  actual fun peek(): BufferedSource

  /** Returns an input stream that reads from this source. */
  fun inputStream(): InputStream
}
