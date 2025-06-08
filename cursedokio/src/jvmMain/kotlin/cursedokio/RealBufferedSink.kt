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
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import cursedokio.internal.commonClose
import cursedokio.internal.commonEmit
import cursedokio.internal.commonEmitCompleteSegments
import cursedokio.internal.commonFlush
import cursedokio.internal.commonTimeout
import cursedokio.internal.commonToString
import cursedokio.internal.commonWrite
import cursedokio.internal.commonWriteAll
import cursedokio.internal.commonWriteByte
import cursedokio.internal.commonWriteDecimalLong
import cursedokio.internal.commonWriteHexadecimalUnsignedLong
import cursedokio.internal.commonWriteInt
import cursedokio.internal.commonWriteIntLe
import cursedokio.internal.commonWriteLong
import cursedokio.internal.commonWriteLongLe
import cursedokio.internal.commonWriteShort
import cursedokio.internal.commonWriteShortLe
import cursedokio.internal.commonWriteUtf8
import cursedokio.internal.commonWriteUtf8CodePoint

internal actual class RealBufferedSink actual constructor(
  @JvmField actual val sink: Sink,
) : BufferedSink {
  @JvmField val bufferField = Buffer()

  @JvmField actual var closed: Boolean = false

  @Suppress("OVERRIDE_BY_INLINE") // Prevent internal code from calling the getter.
  actual override val buffer: Buffer
    inline get() = bufferField

  override fun buffer() = bufferField

  actual override suspend fun write(source: Buffer, byteCount: Long) = commonWrite(source, byteCount)
  actual override suspend fun write(byteString: ByteString) = commonWrite(byteString)
  actual override suspend fun write(byteString: ByteString, offset: Int, byteCount: Int) =
    commonWrite(byteString, offset, byteCount)
  actual override suspend fun writeUtf8(string: String) = commonWriteUtf8(string)
  actual override suspend fun writeUtf8(string: String, beginIndex: Int, endIndex: Int) =
    commonWriteUtf8(string, beginIndex, endIndex)

  actual override suspend fun writeUtf8CodePoint(codePoint: Int) = commonWriteUtf8CodePoint(codePoint)

  override suspend fun writeString(string: String, charset: Charset): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeString(string, charset)
    return emitCompleteSegments()
  }

  override suspend fun writeString(
    string: String,
    beginIndex: Int,
    endIndex: Int,
    charset: Charset,
  ): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeString(string, beginIndex, endIndex, charset)
    return emitCompleteSegments()
  }

  actual override suspend fun write(source: ByteArray) = commonWrite(source)
  actual override suspend fun write(source: ByteArray, offset: Int, byteCount: Int) =
    commonWrite(source, offset, byteCount)

  actual override suspend fun writeAll(source: Source) = commonWriteAll(source)
  actual override suspend fun write(source: Source, byteCount: Long): BufferedSink = commonWrite(source, byteCount)
  actual override suspend fun writeByte(b: Int) = commonWriteByte(b)
  actual override suspend fun writeShort(s: Int) = commonWriteShort(s)
  actual override suspend fun writeShortLe(s: Int) = commonWriteShortLe(s)
  actual override suspend fun writeInt(i: Int) = commonWriteInt(i)
  actual override suspend fun writeIntLe(i: Int) = commonWriteIntLe(i)
  actual override suspend fun writeLong(v: Long) = commonWriteLong(v)
  actual override suspend fun writeLongLe(v: Long) = commonWriteLongLe(v)
  actual override suspend fun writeDecimalLong(v: Long) = commonWriteDecimalLong(v)
  actual override suspend fun writeHexadecimalUnsignedLong(v: Long) = commonWriteHexadecimalUnsignedLong(v)
  actual override suspend fun emitCompleteSegments() = commonEmitCompleteSegments()
  actual override suspend fun emit() = commonEmit()

  actual override suspend fun flush() = commonFlush()

  actual override suspend fun close() = commonClose()
  actual override fun timeout() = commonTimeout()
  override fun toString() = commonToString()
}
