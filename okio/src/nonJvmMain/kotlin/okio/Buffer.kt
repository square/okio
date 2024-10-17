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

import okio.internal.HashFunction
import okio.internal.Hmac
import okio.internal.Md5
import okio.internal.Sha1
import okio.internal.Sha256
import okio.internal.Sha512
import okio.internal.commonClear
import okio.internal.commonClose
import okio.internal.commonCompleteSegmentByteCount
import okio.internal.commonCopy
import okio.internal.commonCopyTo
import okio.internal.commonEquals
import okio.internal.commonExpandBuffer
import okio.internal.commonGet
import okio.internal.commonHashCode
import okio.internal.commonIndexOf
import okio.internal.commonIndexOfElement
import okio.internal.commonNext
import okio.internal.commonRangeEquals
import okio.internal.commonRead
import okio.internal.commonReadAll
import okio.internal.commonReadAndWriteUnsafe
import okio.internal.commonReadByte
import okio.internal.commonReadByteArray
import okio.internal.commonReadByteString
import okio.internal.commonReadDecimalLong
import okio.internal.commonReadFully
import okio.internal.commonReadHexadecimalUnsignedLong
import okio.internal.commonReadInt
import okio.internal.commonReadLong
import okio.internal.commonReadShort
import okio.internal.commonReadUnsafe
import okio.internal.commonReadUtf8
import okio.internal.commonReadUtf8CodePoint
import okio.internal.commonReadUtf8Line
import okio.internal.commonReadUtf8LineStrict
import okio.internal.commonResizeBuffer
import okio.internal.commonSeek
import okio.internal.commonSelect
import okio.internal.commonSkip
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

  actual override fun exhausted(): Boolean = size == 0L

  actual override fun require(byteCount: Long) {
    if (size < byteCount) throw EOFException(null)
  }

  actual override fun request(byteCount: Long): Boolean = size >= byteCount

  actual override fun peek(): BufferedSource = PeekSource(this).buffer()

  actual fun copyTo(
    out: Buffer,
    offset: Long,
    byteCount: Long,
  ): Buffer = commonCopyTo(out, offset, byteCount)

  actual fun copyTo(
    out: Buffer,
    offset: Long,
  ): Buffer = copyTo(out, offset, size - offset)

  actual operator fun get(pos: Long): Byte = commonGet(pos)

  actual fun completeSegmentByteCount(): Long = commonCompleteSegmentByteCount()

  actual override fun readByte(): Byte = commonReadByte()

  actual override fun readShort(): Short = commonReadShort()

  actual override fun readInt(): Int = commonReadInt()

  actual override fun readLong(): Long = commonReadLong()

  actual override fun readShortLe(): Short = readShort().reverseBytes()

  actual override fun readIntLe(): Int = readInt().reverseBytes()

  actual override fun readLongLe(): Long = readLong().reverseBytes()

  actual override fun readDecimalLong(): Long = commonReadDecimalLong()

  actual override fun readHexadecimalUnsignedLong(): Long = commonReadHexadecimalUnsignedLong()

  actual override fun readByteString(): ByteString = commonReadByteString()

  actual override fun readByteString(byteCount: Long): ByteString = commonReadByteString(byteCount)

  actual override fun readFully(sink: Buffer, byteCount: Long): Unit = commonReadFully(sink, byteCount)

  actual override fun readAll(sink: Sink): Long = commonReadAll(sink)

  actual override fun readUtf8(): String = readUtf8(size)

  actual override fun readUtf8(byteCount: Long): String = commonReadUtf8(byteCount)

  actual override fun readUtf8Line(): String? = commonReadUtf8Line()

  actual override fun readUtf8LineStrict(): String = readUtf8LineStrict(Long.MAX_VALUE)

  actual override fun readUtf8LineStrict(limit: Long): String = commonReadUtf8LineStrict(limit)

  actual override fun readUtf8CodePoint(): Int = commonReadUtf8CodePoint()

  actual override fun select(options: Options): Int = commonSelect(options)

  actual override fun <T : Any> select(options: TypedOptions<T>): T? = commonSelect(options)

  actual override fun readByteArray(): ByteArray = commonReadByteArray()

  actual override fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)

  actual override fun read(sink: ByteArray): Int = commonRead(sink)

  actual override fun readFully(sink: ByteArray): Unit = commonReadFully(sink)

  actual override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int =
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

  actual override fun writeAll(source: Source): Long = commonWriteAll(source)

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

  actual override fun write(source: Buffer, byteCount: Long): Unit = commonWrite(source, byteCount)

  actual override fun read(sink: Buffer, byteCount: Long): Long = commonRead(sink, byteCount)

  actual override fun indexOf(b: Byte): Long = indexOf(b, 0, Long.MAX_VALUE)

  actual override fun indexOf(b: Byte, fromIndex: Long): Long = indexOf(b, fromIndex, Long.MAX_VALUE)

  actual override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long =
    commonIndexOf(b, fromIndex, toIndex)

  actual override fun indexOf(bytes: ByteString): Long = indexOf(bytes, 0)

  actual override fun indexOf(bytes: ByteString, fromIndex: Long): Long = commonIndexOf(bytes, fromIndex)

  actual override fun indexOfElement(targetBytes: ByteString): Long = indexOfElement(targetBytes, 0L)

  actual override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long =
    commonIndexOfElement(targetBytes, fromIndex)

  actual override fun rangeEquals(offset: Long, bytes: ByteString): Boolean =
    rangeEquals(offset, bytes, 0, bytes.size)

  actual override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int,
  ): Boolean = commonRangeEquals(offset, bytes, bytesOffset, byteCount)

  actual override fun flush() = Unit

  actual override fun close() = Unit

  actual override fun timeout(): Timeout = Timeout.NONE

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

  actual fun md5() = digest(Md5())

  actual fun sha1() = digest(Sha1())

  actual fun sha256() = digest(Sha256())

  actual fun sha512() = digest(Sha512())

  /** Returns the 160-bit SHA-1 HMAC of this buffer.  */
  actual fun hmacSha1(key: ByteString) = digest(Hmac.sha1(key))

  /** Returns the 256-bit SHA-256 HMAC of this buffer.  */
  actual fun hmacSha256(key: ByteString) = digest(Hmac.sha256(key))

  /** Returns the 512-bit SHA-512 HMAC of this buffer.  */
  actual fun hmacSha512(key: ByteString) = digest(Hmac.sha512(key))

  private fun digest(hash: HashFunction): ByteString {
    forEachSegment { segment ->
      hash.update(segment.data, segment.pos, segment.limit - segment.pos)
    }

    return ByteString(hash.digest())
  }

  private fun forEachSegment(action: (Segment) -> Unit) {
    head?.let { head ->
      var segment: Segment? = head
      do {
        segment?.let(action)
        segment = segment?.next
      } while (segment !== head)
    }
  }

  actual fun readUnsafe(unsafeCursor: UnsafeCursor): UnsafeCursor = commonReadUnsafe(unsafeCursor)

  actual fun readAndWriteUnsafe(unsafeCursor: UnsafeCursor): UnsafeCursor =
    commonReadAndWriteUnsafe(unsafeCursor)

  actual class UnsafeCursor : Closeable {
    actual var buffer: Buffer? = null
    actual var readWrite: Boolean = false

    internal actual var segment: Segment? = null
    actual var offset = -1L
    actual var data: ByteArray? = null
    actual var start = -1
    actual var end = -1

    actual fun next(): Int = commonNext()

    actual fun seek(offset: Long): Int = commonSeek(offset)

    actual fun resizeBuffer(newSize: Long): Long = commonResizeBuffer(newSize)

    actual fun expandBuffer(minByteCount: Int): Long = commonExpandBuffer(minByteCount)

    actual override fun close() {
      commonClose()
    }
  }
}
