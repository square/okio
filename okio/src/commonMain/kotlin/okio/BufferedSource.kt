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

expect interface BufferedSource : Source {
  val buffer: Buffer

  fun exhausted(): Boolean

  fun require(byteCount: Long)

  fun request(byteCount: Long): Boolean

  fun readByte(): Byte

  fun readShort(): Short

  fun readShortLe(): Short

  fun readInt(): Int

  fun readIntLe(): Int

  fun readLong(): Long

  fun readLongLe(): Long

  fun readDecimalLong(): Long

  fun readHexadecimalUnsignedLong(): Long

  fun skip(byteCount: Long)

  fun readByteString(): ByteString

  fun readByteString(byteCount: Long): ByteString

  fun readByteArray(): ByteArray

  fun readByteArray(byteCount: Long): ByteArray

  fun read(sink: ByteArray): Int

  fun readFully(sink: ByteArray)

  fun read(sink: ByteArray, offset: Int, byteCount: Int): Int

  fun readFully(sink: Buffer, byteCount: Long)

  fun readAll(sink: Sink): Long

  fun readUtf8(): String

  fun readUtf8(byteCount: Long): String

  fun readUtf8Line(): String?

  fun readUtf8LineStrict(): String

  fun readUtf8LineStrict(limit: Long): String

  fun readUtf8CodePoint(): Int

  fun indexOf(b: Byte): Long

  fun indexOf(b: Byte, fromIndex: Long): Long

  fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long

  fun indexOf(bytes: ByteString): Long

  fun indexOf(bytes: ByteString, fromIndex: Long): Long

  fun indexOfElement(targetBytes: ByteString): Long

  fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long

  fun rangeEquals(offset: Long, bytes: ByteString): Boolean

  fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean

  fun peek(): BufferedSource
}
