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

import okio.internal.commonClose
import okio.internal.commonExhausted
import okio.internal.commonIndexOf
import okio.internal.commonIndexOfElement
import okio.internal.commonPeek
import okio.internal.commonRangeEquals
import okio.internal.commonRead
import okio.internal.commonReadAll
import okio.internal.commonReadByte
import okio.internal.commonReadByteArray
import okio.internal.commonReadByteString
import okio.internal.commonReadDecimalLong
import okio.internal.commonReadFully
import okio.internal.commonReadHexadecimalUnsignedLong
import okio.internal.commonReadInt
import okio.internal.commonReadIntLe
import okio.internal.commonReadLong
import okio.internal.commonReadLongLe
import okio.internal.commonReadShort
import okio.internal.commonReadShortLe
import okio.internal.commonReadUtf8
import okio.internal.commonReadUtf8CodePoint
import okio.internal.commonReadUtf8Line
import okio.internal.commonReadUtf8LineStrict
import okio.internal.commonRequest
import okio.internal.commonRequire
import okio.internal.commonSelect
import okio.internal.commonSkip
import okio.internal.commonTimeout
import okio.internal.commonToString

internal actual class RealBufferedSource actual constructor(
  actual val source: Source,
) : BufferedSource {
  actual var closed: Boolean = false
  actual override val buffer: Buffer = Buffer()

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

  actual override fun readFully(sink: Buffer, byteCount: Long): Unit =
    commonReadFully(sink, byteCount)
  actual override fun readAll(sink: Sink): Long = commonReadAll(sink)
  actual override fun readUtf8(): String = commonReadUtf8()
  actual override fun readUtf8(byteCount: Long): String = commonReadUtf8(byteCount)
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
    commonIndexOf(b, fromIndex, toIndex)

  actual override fun indexOf(bytes: ByteString): Long = indexOf(bytes, 0L)
  actual override fun indexOf(bytes: ByteString, fromIndex: Long): Long = commonIndexOf(bytes, fromIndex)
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
  actual override fun close(): Unit = commonClose()
  actual override fun timeout(): Timeout = commonTimeout()
  override fun toString(): String = commonToString()
}
