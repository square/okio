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

import okio.internal.commonClear
import okio.internal.commonCompleteSegmentByteCount
import okio.internal.commonCopy
import okio.internal.commonCopyTo
import okio.internal.commonEquals
import okio.internal.commonGet
import okio.internal.commonHashCode
import okio.internal.commonIndexOf
import okio.internal.commonIndexOfElement
import okio.internal.commonRangeEquals
import okio.internal.commonRead
import okio.internal.commonReadAll
import okio.internal.commonReadByte
import okio.internal.commonReadByteArray
import okio.internal.commonReadByteString
import okio.internal.commonReadDecimalLong
import okio.internal.commonReadFully
import okio.internal.commonReadHexadecimalUnsignedLong
import okio.internal.commonSkip
import okio.internal.commonReadInt
import okio.internal.commonReadLong
import okio.internal.commonReadShort
import okio.internal.commonReadUtf8
import okio.internal.commonReadUtf8CodePoint
import okio.internal.commonReadUtf8Line
import okio.internal.commonReadUtf8LineStrict
import okio.internal.commonSelect
import okio.internal.commonSnapshot
import okio.internal.commonWritableSegment
import okio.internal.commonWrite
import okio.internal.commonWriteAll
import okio.internal.commonWriteByte
import okio.internal.commonWriteDecimalLong
import okio.internal.commonWriteHexadecimalUnsignedLong
import okio.internal.commonWriteInt
import okio.internal.commonWriteLong
import okio.internal.commonWriteShort
import okio.internal.commonWriteUtf8
import okio.internal.commonWriteUtf8CodePoint

actual class Buffer : BufferedSource, BufferedSink {
  internal actual var head: Segment? = null

  actual var size: Long = 0L
    internal set

  actual override val buffer: Buffer get() = this

  actual override fun emitCompleteSegments(): Buffer = this // Nowhere to emit to!

  actual override fun emit(): Buffer = this // Nowhere to emit to!

  override fun exhausted(): Boolean = size == 0L

  override fun require(byteCount: Long) {
    if (size < byteCount) throw EOFException()
  }

  override fun request(byteCount: Long): Boolean = size >= byteCount

  override fun peek(): BufferedSource = PeekSource(this).buffer()

  actual fun copyTo(
    out: Buffer,
    offset: Long,
    byteCount: Long
  ): Buffer = commonCopyTo(out, offset, byteCount)

  actual fun copyTo(
    out: Buffer,
    offset: Long
  ): Buffer = copyTo(out, offset, size - offset)

  actual operator fun get(pos: Long): Byte = commonGet(pos)

  actual fun completeSegmentByteCount(): Long = commonCompleteSegmentByteCount()

  override fun readByte(): Byte = commonReadByte()

  override fun readShort(): Short = commonReadShort()

  override fun readInt(): Int = commonReadInt()

  override fun readLong(): Long = commonReadLong()

  override fun readShortLe(): Short = readShort().reverseBytes()

  override fun readIntLe(): Int = readInt().reverseBytes()

  override fun readLongLe(): Long = readLong().reverseBytes()

  override fun readDecimalLong(): Long = commonReadDecimalLong()

  override fun readHexadecimalUnsignedLong(): Long = commonReadHexadecimalUnsignedLong()

  override fun readByteString(): ByteString = commonReadByteString()

  override fun readByteString(byteCount: Long): ByteString = commonReadByteString(byteCount)

  override fun readFully(sink: Buffer, byteCount: Long): Unit = commonReadFully(sink, byteCount)

  override fun readAll(sink: Sink): Long = commonReadAll(sink)

  override fun readUtf8(): String = readUtf8(size)

  override fun readUtf8(byteCount: Long): String = commonReadUtf8(byteCount)

  override fun readUtf8Line(): String? = commonReadUtf8Line()

  override fun readUtf8LineStrict(): String = readUtf8LineStrict(Long.MAX_VALUE)

  override fun readUtf8LineStrict(limit: Long): String = commonReadUtf8LineStrict(limit)

  override fun readUtf8CodePoint(): Int = commonReadUtf8CodePoint()

  override fun select(options: Options): Int = commonSelect(options)

  override fun readByteArray(): ByteArray = commonReadByteArray()

  override fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)

  override fun read(sink: ByteArray): Int = commonRead(sink)

  override fun readFully(sink: ByteArray): Unit = commonReadFully(sink)

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int =
    commonRead(sink, offset, byteCount)

  actual fun clear(): Unit = commonClear()

  actual override fun skip(byteCount: Long): Unit = commonSkip(byteCount)

  actual override fun write(byteString: ByteString): Buffer = commonWrite(byteString)

  actual override fun write(byteString: ByteString, offset: Int, byteCount: Int) =
    commonWrite(byteString, offset, byteCount)

  internal actual fun writableSegment(minimumCapacity: Int): Segment =
    commonWritableSegment(minimumCapacity)

  actual override fun writeUtf8(string: String): Buffer = writeUtf8(string, 0, string.length)

  actual override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): Buffer =
    commonWriteUtf8(string, beginIndex, endIndex)

  actual override fun writeUtf8CodePoint(codePoint: Int): Buffer =
    commonWriteUtf8CodePoint(codePoint)

  actual override fun write(source: ByteArray): Buffer = commonWrite(source)

  actual override fun write(source: ByteArray, offset: Int, byteCount: Int): Buffer =
    commonWrite(source, offset, byteCount)

  override fun writeAll(source: Source): Long = commonWriteAll(source)

  actual override fun write(source: Source, byteCount: Long): Buffer =
    commonWrite(source, byteCount)

  actual override fun writeByte(b: Int): Buffer = commonWriteByte(b)

  actual override fun writeShort(s: Int): Buffer = commonWriteShort(s)

  actual override fun writeShortLe(s: Int): Buffer = writeShort(s.toShort().reverseBytes().toInt())

  actual override fun writeInt(i: Int): Buffer = commonWriteInt(i)

  actual override fun writeIntLe(i: Int): Buffer = writeInt(i.reverseBytes())

  actual override fun writeLong(v: Long): Buffer = commonWriteLong(v)

  actual override fun writeLongLe(v: Long): Buffer = writeLong(v.reverseBytes())

  actual override fun writeDecimalLong(v: Long): Buffer = commonWriteDecimalLong(v)

  actual override fun writeHexadecimalUnsignedLong(v: Long): Buffer =
    commonWriteHexadecimalUnsignedLong(v)

  override fun write(source: Buffer, byteCount: Long): Unit = commonWrite(source, byteCount)

  override fun read(sink: Buffer, byteCount: Long): Long = commonRead(sink, byteCount)

  override fun indexOf(b: Byte): Long = indexOf(b, 0, Long.MAX_VALUE)

  override fun indexOf(b: Byte, fromIndex: Long): Long = indexOf(b, fromIndex, Long.MAX_VALUE)

  override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long =
    commonIndexOf(b, fromIndex, toIndex)

  override fun indexOf(bytes: ByteString): Long = indexOf(bytes, 0)

  override fun indexOf(bytes: ByteString, fromIndex: Long): Long = commonIndexOf(bytes, fromIndex)

  override fun indexOfElement(targetBytes: ByteString): Long = indexOfElement(targetBytes, 0L)

  override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long =
    commonIndexOfElement(targetBytes, fromIndex)

  override fun rangeEquals(offset: Long, bytes: ByteString): Boolean =
    rangeEquals(offset, bytes, 0, bytes.size)

  override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int
  ): Boolean = commonRangeEquals(offset, bytes, bytesOffset, byteCount)

  override fun flush() {
  }

  override fun close() {
  }

  override fun timeout(): Timeout = Timeout.NONE

  override fun equals(other: Any?): Boolean = commonEquals(other)

  override fun hashCode(): Int = commonHashCode()

  /**
   * Returns a human-readable string that describes the contents of this buffer. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  override fun toString() = snapshot().toString()

  actual fun copy(): Buffer = commonCopy()

  actual fun snapshot(): ByteString = commonSnapshot()

  actual fun snapshot(byteCount: Int): ByteString = commonSnapshot(byteCount)
}
