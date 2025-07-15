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

import okio.internal.commonClose
import okio.internal.commonEmit
import okio.internal.commonEmitCompleteSegments
import okio.internal.commonFlush
import okio.internal.commonTimeout
import okio.internal.commonToString
import okio.internal.commonWrite
import okio.internal.commonWriteAll
import okio.internal.commonWriteByte
import okio.internal.commonWriteDecimalLong
import okio.internal.commonWriteHexadecimalUnsignedLong
import okio.internal.commonWriteInt
import okio.internal.commonWriteIntLe
import okio.internal.commonWriteLong
import okio.internal.commonWriteLongLe
import okio.internal.commonWriteShort
import okio.internal.commonWriteShortLe
import okio.internal.commonWriteUtf8
import okio.internal.commonWriteUtf8CodePoint

internal actual class RealBufferedSink actual constructor(
  actual val sink: Sink,
) : BufferedSink {
  actual var closed: Boolean = false
  actual override val buffer = Buffer()

  actual override fun write(source: Buffer, byteCount: Long) = commonWrite(source, byteCount)
  actual override fun write(byteString: ByteString) = commonWrite(byteString)
  actual override fun write(byteString: ByteString, offset: Int, byteCount: Int) =
    commonWrite(byteString, offset, byteCount)
  actual override fun writeUtf8(string: String) = commonWriteUtf8(string)
  actual override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int) =
    commonWriteUtf8(string, beginIndex, endIndex)

  actual override fun writeUtf8CodePoint(codePoint: Int) = commonWriteUtf8CodePoint(codePoint)
  actual override fun write(source: ByteArray) = commonWrite(source)
  actual override fun write(source: ByteArray, offset: Int, byteCount: Int) =
    commonWrite(source, offset, byteCount)

  actual override fun writeAll(source: Source) = commonWriteAll(source)
  actual override fun write(source: Source, byteCount: Long): BufferedSink = commonWrite(source, byteCount)
  actual override fun writeByte(b: Int) = commonWriteByte(b)
  actual override fun writeShort(s: Int) = commonWriteShort(s)
  actual override fun writeShortLe(s: Int) = commonWriteShortLe(s)
  actual override fun writeInt(i: Int) = commonWriteInt(i)
  actual override fun writeIntLe(i: Int) = commonWriteIntLe(i)
  actual override fun writeLong(v: Long) = commonWriteLong(v)
  actual override fun writeLongLe(v: Long) = commonWriteLongLe(v)
  actual override fun writeDecimalLong(v: Long) = commonWriteDecimalLong(v)
  actual override fun writeHexadecimalUnsignedLong(v: Long) = commonWriteHexadecimalUnsignedLong(v)
  actual override fun emitCompleteSegments() = commonEmitCompleteSegments()
  actual override fun emit() = commonEmit()
  actual override fun flush() = commonFlush()
  actual override fun close() = commonClose()
  actual override fun timeout() = commonTimeout()
  override fun toString() = commonToString()
}
