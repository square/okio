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
package cursedokio

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import cursedokio.internal.commonClose
import cursedokio.internal.commonExhausted
import cursedokio.internal.commonIndexOf
import cursedokio.internal.commonIndexOfElement
import cursedokio.internal.commonPeek
import cursedokio.internal.commonRangeEquals
import cursedokio.internal.commonRead
import cursedokio.internal.commonReadAll
import cursedokio.internal.commonReadByte
import cursedokio.internal.commonReadByteArray
import cursedokio.internal.commonReadByteString
import cursedokio.internal.commonReadDecimalLong
import cursedokio.internal.commonReadFully
import cursedokio.internal.commonReadHexadecimalUnsignedLong
import cursedokio.internal.commonReadInt
import cursedokio.internal.commonReadIntLe
import cursedokio.internal.commonReadLong
import cursedokio.internal.commonReadLongLe
import cursedokio.internal.commonReadShort
import cursedokio.internal.commonReadShortLe
import cursedokio.internal.commonReadUtf8
import cursedokio.internal.commonReadUtf8CodePoint
import cursedokio.internal.commonReadUtf8Line
import cursedokio.internal.commonReadUtf8LineStrict
import cursedokio.internal.commonRequest
import cursedokio.internal.commonRequire
import cursedokio.internal.commonSelect
import cursedokio.internal.commonSkip
import cursedokio.internal.commonTimeout
import cursedokio.internal.commonToString

internal actual class RealBufferedSource actual constructor(
  @JvmField actual val source: Source,
) : BufferedSource {
  @JvmField val bufferField = Buffer()

  @JvmField actual var closed: Boolean = false

  @Suppress("OVERRIDE_BY_INLINE") // Prevent internal code from calling the getter.
  actual override val buffer: Buffer
    inline get() = bufferField

  override fun buffer() = bufferField

  actual override fun read(sink: Buffer, byteCount: Long): Long = commonRead(sink, byteCount)
  actual override fun exhausted(): Boolean = commonExhausted()
  actual override fun require(byteCount: Long): Unit = commonRequire(byteCount)
  actual override fun request(byteCount: Long): Boolean = commonRequest(byteCount)
  actual override fun readByte(): Byte = commonReadByte()
  actual override fun readByteString(): ByteString = commonReadByteString()
  actual override fun readByteString(byteCount: Long): ByteString = commonReadByteString(byteCount)
  actual override fun select(options: Options): Int = commonSelect(options)
  actual override fun <T : Any> select(options: TypedOptions<T>): T? = commonSelect(options)
  actual override fun readByteArray(): ByteArray = commonReadByteArray()
  actual override fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)
  actual override fun read(sink: ByteArray): Int = read(sink, 0, sink.size)
  actual override fun readFully(sink: ByteArray): Unit = commonReadFully(sink)
  actual override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int =
    commonRead(sink, offset, byteCount)

  override fun read(sink: ByteBuffer): Int {
    if (buffer.size == 0L) {
      val read = source.read(buffer, Segment.SIZE.toLong())
      if (read == -1L) return -1
    }

    return buffer.read(sink)
  }

  actual override fun readFully(sink: Buffer, byteCount: Long): Unit =
    commonReadFully(sink, byteCount)
  actual override fun readAll(sink: Sink): Long = commonReadAll(sink)
  actual override fun readUtf8(): String = commonReadUtf8()
  actual override fun readUtf8(byteCount: Long): String = commonReadUtf8(byteCount)

  override fun readString(charset: Charset): String {
    buffer.writeAll(source)
    return buffer.readString(charset)
  }

  override fun readString(byteCount: Long, charset: Charset): String {
    require(byteCount)
    return buffer.readString(byteCount, charset)
  }

  actual override fun readUtf8Line(): String? = commonReadUtf8Line()
  actual override fun readUtf8LineStrict() = readUtf8LineStrict(Long.MAX_VALUE)
  actual override fun readUtf8LineStrict(limit: Long): String = commonReadUtf8LineStrict(limit)
  actual override fun readUtf8CodePoint(): Int = commonReadUtf8CodePoint()
  actual override fun readShort(): Short = commonReadShort()
  actual override fun readShortLe(): Short = commonReadShortLe()
  actual override fun readInt(): Int = commonReadInt()
  actual override fun readIntLe(): Int = commonReadIntLe()
  actual override fun readLong(): Long = commonReadLong()
  actual override fun readLongLe(): Long = commonReadLongLe()
  actual override fun readDecimalLong(): Long = commonReadDecimalLong()
  actual override fun readHexadecimalUnsignedLong(): Long = commonReadHexadecimalUnsignedLong()
  actual override fun skip(byteCount: Long): Unit = commonSkip(byteCount)
  actual override fun indexOf(b: Byte): Long = indexOf(b, 0L, Long.MAX_VALUE)
  actual override fun indexOf(b: Byte, fromIndex: Long): Long =
    indexOf(b, fromIndex, Long.MAX_VALUE)
  actual override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long =
    commonIndexOf(b, fromIndex = fromIndex, toIndex = toIndex)

  actual override fun indexOf(bytes: ByteString): Long = indexOf(bytes, 0L)
  actual override fun indexOf(bytes: ByteString, fromIndex: Long): Long =
    indexOf(bytes, fromIndex, Long.MAX_VALUE)
  actual override fun indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long =
    commonIndexOf(bytes, fromIndex = fromIndex, toIndex = toIndex)
  actual override fun indexOfElement(targetBytes: ByteString): Long =
    indexOfElement(targetBytes, 0L)
  actual override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long =
    commonIndexOfElement(targetBytes, fromIndex)

  actual override fun rangeEquals(offset: Long, bytes: ByteString) = rangeEquals(
    offset,
    bytes,
    0,
    bytes.size,
  )

  actual override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int,
  ): Boolean = commonRangeEquals(offset, bytes, bytesOffset, byteCount)

  actual override fun peek(): BufferedSource = commonPeek()

  override fun inputStream(): InputStream {
    return object : InputStream() {
      override fun read(): Int {
        if (closed) throw IOException("closed")
        if (buffer.size == 0L) {
          val count = source.read(buffer, Segment.SIZE.toLong())
          if (count == -1L) return -1
        }
        return buffer.readByte() and 0xff
      }

      override fun read(data: ByteArray, offset: Int, byteCount: Int): Int {
        if (closed) throw IOException("closed")
        checkOffsetAndCount(data.size.toLong(), offset.toLong(), byteCount.toLong())

        if (buffer.size == 0L) {
          val count = source.read(buffer, Segment.SIZE.toLong())
          if (count == -1L) return -1
        }

        return buffer.read(data, offset, byteCount)
      }

      override fun available(): Int {
        if (closed) throw IOException("closed")
        return minOf(buffer.size, Integer.MAX_VALUE).toInt()
      }

      override fun close() = this@RealBufferedSource.close()

      override fun toString() = "${this@RealBufferedSource}.inputStream()"

      override fun transferTo(out: OutputStream): Long {
        if (closed) throw IOException("closed")
        var count = 0L
        while (true) {
          if (buffer.size == 0L) {
            val read = source.read(buffer, Segment.SIZE.toLong())
            if (read == -1L) break
          }
          count += buffer.size
          buffer.writeTo(out)
        }
        return count
      }
    }
  }

  override fun isOpen() = !closed

  actual override fun close(): Unit = commonClose()
  actual override fun timeout(): Timeout = commonTimeout()
  override fun toString(): String = commonToString()
}
