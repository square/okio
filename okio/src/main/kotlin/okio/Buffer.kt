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

expect class Buffer : BufferedSource, BufferedSink {
  override val buffer: Buffer

  override fun emitCompleteSegments(): Buffer

  override fun emit(): Buffer

  override fun exhausted(): Boolean

  override fun require(byteCount: Long)

  override fun request(byteCount: Long): Boolean

  override fun peek(): BufferedSource

  override fun readByte(): Byte

  override fun readShort(): Short

  override fun readInt(): Int

  override fun readLong(): Long

  override fun readShortLe(): Short

  override fun readIntLe(): Int

  override fun readLongLe(): Long

  override fun readDecimalLong(): Long

  override fun readHexadecimalUnsignedLong(): Long

  override fun readByteString(): ByteString

  override fun readByteString(byteCount: Long): ByteString

  override fun readFully(sink: Buffer, byteCount: Long)

  override fun readAll(sink: Sink): Long

  override fun readUtf8(): String

  override fun readUtf8(byteCount: Long): String

  override fun readUtf8Line(): String?

  override fun readUtf8LineStrict(): String

  override fun readUtf8LineStrict(limit: Long): String

  override fun readUtf8CodePoint(): Int

  override fun readByteArray(): ByteArray

  override fun readByteArray(byteCount: Long): ByteArray

  override fun read(sink: ByteArray): Int

  override fun readFully(sink: ByteArray)

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int

  override fun skip(byteCount: Long)

  override fun write(byteString: ByteString): Buffer

  override fun writeUtf8(string: String): Buffer

  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer

  override fun writeUtf8CodePoint(codePoint: Int): Buffer

  override fun write(source: ByteArray): Buffer

  override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer

  override fun writeAll(source: Source): Long

  override fun write(source: Source, byteCount: Long): BufferedSink

  override fun writeByte(b: Int): Buffer

  override fun writeShort(s: Int): Buffer

  override fun writeShortLe(s: Int): Buffer

  override fun writeInt(i: Int): Buffer

  override fun writeIntLe(i: Int): Buffer

  override fun writeLong(v: Long): Buffer

  override fun writeLongLe(v: Long): Buffer

  override fun writeDecimalLong(v: Long): Buffer

  override fun writeHexadecimalUnsignedLong(v: Long): Buffer

  override fun write(source: Buffer, byteCount: Long)

  override fun read(sink: Buffer, byteCount: Long): Long

  override fun indexOf(b: Byte): Long

  override fun indexOf(b: Byte, fromIndex: Long): Long

  override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long

  override fun indexOf(bytes: ByteString): Long

  override fun indexOf(bytes: ByteString, fromIndex: Long): Long

  override fun indexOfElement(targetBytes: ByteString): Long

  override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long

  override fun rangeEquals(offset: Long, bytes: ByteString): Boolean

  override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int
  ): Boolean

  override fun flush()

  override fun close()

  override fun timeout(): Timeout
}
