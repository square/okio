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

  actual override suspend fun read(sink: Buffer, byteCount: Long): Long = commonRead(sink, byteCount)
  actual override suspend fun exhausted(): Boolean = commonExhausted()
  actual override suspend fun require(byteCount: Long): Unit = commonRequire(byteCount)
  actual override suspend fun request(byteCount: Long): Boolean = commonRequest(byteCount)
  actual override suspend fun readByte(): Byte = commonReadByte()
  actual override suspend fun readByteString(): ByteString = commonReadByteString()
  actual override suspend fun readByteString(byteCount: Long): ByteString = commonReadByteString(byteCount)
  actual override suspend fun select(options: Options): Int = commonSelect(options)
  actual override suspend fun <T : Any> select(options: TypedOptions<T>): T? = commonSelect(options)
  actual override suspend fun readByteArray(): ByteArray = commonReadByteArray()
  actual override suspend fun readByteArray(byteCount: Long): ByteArray = commonReadByteArray(byteCount)
  actual override suspend fun read(sink: ByteArray): Int = read(sink, 0, sink.size)
  actual override suspend fun readFully(sink: ByteArray): Unit = commonReadFully(sink)
  actual override suspend fun read(sink: ByteArray, offset: Int, byteCount: Int): Int =
    commonRead(sink, offset, byteCount)

  actual override suspend fun readFully(sink: Buffer, byteCount: Long): Unit =
    commonReadFully(sink, byteCount)
  actual override suspend fun readAll(sink: Sink): Long = commonReadAll(sink)
  actual override suspend fun readUtf8(): String = commonReadUtf8()
  actual override suspend fun readUtf8(byteCount: Long): String = commonReadUtf8(byteCount)

  override suspend fun readString(charset: Charset): String {
    buffer.writeAll(source)
    return buffer.readString(charset)
  }

  override suspend fun readString(byteCount: Long, charset: Charset): String {
    require(byteCount)
    return buffer.readString(byteCount, charset)
  }

  actual override suspend fun readUtf8Line(): String? = commonReadUtf8Line()
  actual override suspend fun readUtf8LineStrict() = readUtf8LineStrict(Long.MAX_VALUE)
  actual override suspend fun readUtf8LineStrict(limit: Long): String = commonReadUtf8LineStrict(limit)
  actual override suspend fun readUtf8CodePoint(): Int = commonReadUtf8CodePoint()
  actual override suspend fun readShort(): Short = commonReadShort()
  actual override suspend fun readShortLe(): Short = commonReadShortLe()
  actual override suspend fun readInt(): Int = commonReadInt()
  actual override suspend fun readIntLe(): Int = commonReadIntLe()
  actual override suspend fun readLong(): Long = commonReadLong()
  actual override suspend fun readLongLe(): Long = commonReadLongLe()
  actual override suspend fun readDecimalLong(): Long = commonReadDecimalLong()
  actual override suspend fun readHexadecimalUnsignedLong(): Long = commonReadHexadecimalUnsignedLong()
  actual override suspend fun skip(byteCount: Long): Unit = commonSkip(byteCount)
  actual override suspend fun indexOf(b: Byte): Long = indexOf(b, 0L, Long.MAX_VALUE)
  actual override suspend fun indexOf(b: Byte, fromIndex: Long): Long =
    indexOf(b, fromIndex, Long.MAX_VALUE)
  actual override suspend fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long =
    commonIndexOf(b, fromIndex = fromIndex, toIndex = toIndex)

  actual override suspend fun indexOf(bytes: ByteString): Long = indexOf(bytes, 0L)
  actual override suspend fun indexOf(bytes: ByteString, fromIndex: Long): Long =
    indexOf(bytes, fromIndex, Long.MAX_VALUE)
  actual override suspend fun indexOf(bytes: ByteString, fromIndex: Long, toIndex: Long): Long =
    commonIndexOf(bytes, fromIndex = fromIndex, toIndex = toIndex)
  actual override suspend fun indexOfElement(targetBytes: ByteString): Long =
    indexOfElement(targetBytes, 0L)
  actual override suspend fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long =
    commonIndexOfElement(targetBytes, fromIndex)

  actual override suspend fun rangeEquals(offset: Long, bytes: ByteString) = rangeEquals(
    offset,
    bytes,
    0,
    bytes.size,
  )

  actual override suspend fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int,
  ): Boolean = commonRangeEquals(offset, bytes, bytesOffset, byteCount)

  actual override fun peek(): BufferedSource = commonPeek()

  actual override suspend fun close(): Unit = commonClose()
  actual override fun timeout(): Timeout = commonTimeout()
  override fun toString(): String = commonToString()
}
