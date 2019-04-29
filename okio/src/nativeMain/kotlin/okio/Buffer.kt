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

import okio.Timeout.Companion
import okio.internal.commonCopyTo
import okio.internal.commonGet
import okio.internal.commonRead
import okio.internal.commonReadByte
import okio.internal.commonReadByteArray
import okio.internal.commonReadByteString
import okio.internal.commonReadFully
import okio.internal.commonWritableSegment
import okio.internal.commonWrite

actual class Buffer : BufferedSource, BufferedSink {
  internal actual var head: Segment? = null

  actual var size: Long = 0L
    internal set

  actual override val buffer: Buffer get() = this

  actual override fun emitCompleteSegments(): Buffer = this // Nowhere to emit to!

  actual override fun emit(): Buffer = this // Nowhere to emit to!

  override fun exhausted(): Boolean = TODO()

  override fun require(byteCount: Long) {
    TODO()
  }

  override fun request(byteCount: Long): Boolean = TODO()

  override fun peek(): BufferedSource = PeekSource(this).buffer()

  actual fun copyTo(
    out: Buffer,
    offset: Long,
    byteCount: Long
  ): Buffer = commonCopyTo(out, offset, byteCount)

  /**
   * Overload of [copyTo] with byteCount = size - offset, work around for
   *  https://youtrack.jetbrains.com/issue/KT-30847
   */
  actual fun copyTo(
    out: Buffer,
    offset: Long
  ): Buffer = copyTo(out, offset, size - offset)

  operator fun get(pos: Long): Byte = commonGet(pos)

  override fun readByte(): Byte = commonReadByte()

  override fun readShort(): Short = TODO()

  override fun readInt(): Int = TODO()

  override fun readLong(): Long = TODO()

  override fun readShortLe(): Short = TODO()

  override fun readIntLe(): Int = TODO()

  override fun readLongLe(): Long = TODO()

  override fun readDecimalLong(): Long = TODO()

  override fun readHexadecimalUnsignedLong(): Long = TODO()

  override fun readByteString(): ByteString = commonReadByteString()

  override fun readByteString(byteCount: Long): ByteString = commonReadByteString(byteCount)

  override fun readFully(sink: Buffer, byteCount: Long) {
    TODO()
  }

  override fun readAll(sink: Sink): Long = TODO()

  override fun readUtf8(): String = TODO()

  override fun readUtf8(byteCount: Long): String = TODO()

  override fun readUtf8Line(): String? = TODO()

  override fun readUtf8LineStrict(): String = TODO()

  override fun readUtf8LineStrict(limit: Long): String = TODO()

  override fun readUtf8CodePoint(): Int = TODO()

  override fun readByteArray(): ByteArray = commonReadByteArray()

  override fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)

  override fun read(sink: ByteArray): Int = commonRead(sink)

  override fun readFully(sink: ByteArray) = commonReadFully(sink)

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int =
    commonRead(sink, offset, byteCount)

  override fun skip(byteCount: Long) {
    TODO()
  }

  actual override fun write(byteString: ByteString): Buffer = commonWrite(byteString)

  actual override fun writeUtf8(string: String): Buffer = TODO()

  internal actual fun writableSegment(minimumCapacity: Int): Segment =
    commonWritableSegment(minimumCapacity)

  actual override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer = TODO()

  actual override fun writeUtf8CodePoint(codePoint: Int): Buffer = TODO()

  actual override fun write(source: ByteArray): Buffer = commonWrite(source)

  actual override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer =
    commonWrite(source, offset, byteCount)

  override fun writeAll(source: Source): Long = TODO()

  actual override fun write(source: Source, byteCount: Long): Buffer = TODO()

  actual override fun writeByte(b: Int): Buffer = TODO()

  actual override fun writeShort(s: Int): Buffer = TODO()

  actual override fun writeShortLe(s: Int): Buffer = TODO()

  actual override fun writeInt(i: Int): Buffer = TODO()

  actual override fun writeIntLe(i: Int): Buffer = TODO()

  actual override fun writeLong(v: Long): Buffer = TODO()

  actual override fun writeLongLe(v: Long): Buffer = TODO()

  actual override fun writeDecimalLong(v: Long): Buffer = TODO()

  actual override fun writeHexadecimalUnsignedLong(v: Long): Buffer = TODO()

  override fun write(source: Buffer, byteCount: Long) {
    TODO()
  }

  override fun read(sink: Buffer, byteCount: Long): Long = TODO()

  override fun indexOf(b: Byte): Long = TODO()

  override fun indexOf(b: Byte, fromIndex: Long): Long = TODO()

  override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long = TODO()

  override fun indexOf(bytes: ByteString): Long = TODO()

  override fun indexOf(bytes: ByteString, fromIndex: Long): Long = TODO()

  override fun indexOfElement(targetBytes: ByteString): Long = TODO()

  override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long = TODO()

  override fun rangeEquals(offset: Long, bytes: ByteString): Boolean = TODO()

  override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int
  ): Boolean = TODO()

  override fun flush() {
  }

  override fun close() {
  }

  override fun timeout(): Timeout = Timeout.NONE
}
