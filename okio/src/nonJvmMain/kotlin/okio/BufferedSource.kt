/*
 * Copyright (C) 2019 Square, Inc.
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

actual interface BufferedSource : Source {
  actual val buffer: Buffer

  actual fun exhausted(): Boolean

  actual fun require(byteCount: Long)

  actual fun request(byteCount: Long): Boolean

  actual fun readByte(): Byte

  actual fun readShort(): Short

  actual fun readShortLe(): Short

  actual fun readInt(): Int

  actual fun readIntLe(): Int

  actual fun readLong(): Long

  actual fun readLongLe(): Long

  actual fun readDecimalLong(): Long

  actual fun readHexadecimalUnsignedLong(): Long

  actual fun skip(byteCount: Long)

  actual fun readByteString(): ByteString

  actual fun readByteString(byteCount: Long): ByteString

  actual fun select(options: Options): Int

  actual fun readByteArray(): ByteArray

  actual fun readByteArray(byteCount: Long): ByteArray

  actual fun read(sink: ByteArray): Int

  actual fun readFully(sink: ByteArray)

  actual fun read(sink: ByteArray, offset: Int, byteCount: Int): Int

  actual fun readFully(sink: Buffer, byteCount: Long)

  actual fun readAll(sink: Sink): Long

  actual fun readUtf8(): String

  actual fun readUtf8(byteCount: Long): String

  actual fun readUtf8Line(): String?

  actual fun readUtf8LineStrict(): String

  actual fun readUtf8LineStrict(limit: Long): String

  actual fun readUtf8CodePoint(): Int

  actual fun indexOf(b: Byte): Long

  actual fun indexOf(b: Byte, fromIndex: Long): Long

  actual fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long

  actual fun indexOf(bytes: ByteString): Long

  actual fun indexOf(bytes: ByteString, fromIndex: Long): Long

  actual fun indexOfElement(targetBytes: ByteString): Long

  actual fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long

  actual fun rangeEquals(offset: Long, bytes: ByteString): Boolean

  actual fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean

  actual fun peek(): BufferedSource
}
