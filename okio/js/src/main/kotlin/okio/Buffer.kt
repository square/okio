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

actual class Buffer : BufferedSource, BufferedSink {
  actual override val buffer: Buffer get() = this

  actual override fun emitCompleteSegments(): Buffer = this // Nowhere to emit to!

  actual override fun emit(): Buffer = this // Nowhere to emit to!

  override fun exhausted(): Boolean = throw UnsupportedOperationException()

  override fun require(byteCount: Long) {
    throw UnsupportedOperationException()
  }

  override fun request(byteCount: Long): Boolean = throw UnsupportedOperationException()

  override fun peek(): BufferedSource = throw UnsupportedOperationException()

  override fun readByte(): Byte = throw UnsupportedOperationException()

  override fun readShort(): Short = throw UnsupportedOperationException()

  override fun readInt(): Int = throw UnsupportedOperationException()

  override fun readLong(): Long = throw UnsupportedOperationException()

  override fun readShortLe(): Short = throw UnsupportedOperationException()

  override fun readIntLe(): Int = throw UnsupportedOperationException()

  override fun readLongLe(): Long = throw UnsupportedOperationException()

  override fun readDecimalLong(): Long = throw UnsupportedOperationException()

  override fun readHexadecimalUnsignedLong(): Long = throw UnsupportedOperationException()

  override fun readByteString(): ByteString = throw UnsupportedOperationException()

  override fun readByteString(byteCount: Long): ByteString = throw UnsupportedOperationException()

  override fun readFully(sink: Buffer, byteCount: Long) {
    throw UnsupportedOperationException()
  }

  override fun readAll(sink: Sink): Long = throw UnsupportedOperationException()

  override fun readUtf8(): String = throw UnsupportedOperationException()

  override fun readUtf8(byteCount: Long): String = throw UnsupportedOperationException()

  override fun readUtf8Line(): String? = throw UnsupportedOperationException()

  override fun readUtf8LineStrict(): String = throw UnsupportedOperationException()

  override fun readUtf8LineStrict(limit: Long): String = throw UnsupportedOperationException()

  override fun readUtf8CodePoint(): Int = throw UnsupportedOperationException()

  override fun readByteArray(): ByteArray = throw UnsupportedOperationException()

  override fun readByteArray(byteCount: Long): ByteArray = throw UnsupportedOperationException()

  override fun read(sink: ByteArray): Int = throw UnsupportedOperationException()

  override fun readFully(sink: ByteArray) {
    throw UnsupportedOperationException()
  }

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int = throw UnsupportedOperationException()

  override fun skip(byteCount: Long) {
    throw UnsupportedOperationException()
  }

  actual override fun write(byteString: ByteString): Buffer = throw UnsupportedOperationException()

  actual override fun writeUtf8(string: String): Buffer = throw UnsupportedOperationException()

  actual override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer = throw UnsupportedOperationException()

  actual override fun writeUtf8CodePoint(codePoint: Int): Buffer = throw UnsupportedOperationException()

  actual override fun write(source: ByteArray): Buffer = throw UnsupportedOperationException()

  actual override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer = throw UnsupportedOperationException()

  override fun writeAll(source: Source): Long = throw UnsupportedOperationException()

  actual override fun write(source: Source, byteCount: Long): Buffer = throw UnsupportedOperationException()

  actual override fun writeByte(b: Int): Buffer = throw UnsupportedOperationException()

  actual override fun writeShort(s: Int): Buffer = throw UnsupportedOperationException()

  actual override fun writeShortLe(s: Int): Buffer = throw UnsupportedOperationException()

  actual override fun writeInt(i: Int): Buffer = throw UnsupportedOperationException()

  actual override fun writeIntLe(i: Int): Buffer = throw UnsupportedOperationException()

  actual override fun writeLong(v: Long): Buffer = throw UnsupportedOperationException()

  actual override fun writeLongLe(v: Long): Buffer = throw UnsupportedOperationException()

  actual override fun writeDecimalLong(v: Long): Buffer = throw UnsupportedOperationException()

  actual override fun writeHexadecimalUnsignedLong(v: Long): Buffer = throw UnsupportedOperationException()

  override fun write(source: Buffer, byteCount: Long) {
    throw UnsupportedOperationException()
  }

  override fun read(sink: Buffer, byteCount: Long): Long = throw UnsupportedOperationException()

  override fun indexOf(b: Byte): Long = throw UnsupportedOperationException()

  override fun indexOf(b: Byte, fromIndex: Long): Long = throw UnsupportedOperationException()

  override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long = throw UnsupportedOperationException()

  override fun indexOf(bytes: ByteString): Long = throw UnsupportedOperationException()

  override fun indexOf(bytes: ByteString, fromIndex: Long): Long = throw UnsupportedOperationException()

  override fun indexOfElement(targetBytes: ByteString): Long = throw UnsupportedOperationException()

  override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long = throw UnsupportedOperationException()

  override fun rangeEquals(offset: Long, bytes: ByteString): Boolean = throw UnsupportedOperationException()

  override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int
  ): Boolean = throw UnsupportedOperationException()

  override fun flush() {
  }

  override fun close() {
  }

  override fun timeout() = Timeout.NONE
}
