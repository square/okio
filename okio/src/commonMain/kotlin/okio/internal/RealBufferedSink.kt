/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO move to RealBufferedSink class: https://youtrack.jetbrains.com/issue/KT-20427
@file:Suppress("NOTHING_TO_INLINE")

@file:JvmName("-RealBufferedSink") // A leading '-' hides this class from Java.

package okio.internal

import kotlin.jvm.JvmName
import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import okio.EOFException
import okio.RealBufferedSink
import okio.Segment
import okio.Source

internal inline fun RealBufferedSink.commonWrite(source: Buffer, byteCount: Long) {
  check(!closed) { "closed" }
  buffer.write(source, byteCount)
  emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWrite(byteString: ByteString): BufferedSink {
  check(!closed) { "closed" }
  buffer.write(byteString)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWrite(
  byteString: ByteString,
  offset: Int,
  byteCount: Int,
): BufferedSink {
  check(!closed) { "closed" }
  buffer.write(byteString, offset, byteCount)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteUtf8(string: String): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeUtf8(string)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteUtf8(
  string: String,
  beginIndex: Int,
  endIndex: Int,
): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeUtf8(string, beginIndex, endIndex)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteUtf8CodePoint(codePoint: Int): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeUtf8CodePoint(codePoint)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWrite(source: ByteArray): BufferedSink {
  check(!closed) { "closed" }
  buffer.write(source)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWrite(
  source: ByteArray,
  offset: Int,
  byteCount: Int,
): BufferedSink {
  check(!closed) { "closed" }
  buffer.write(source, offset, byteCount)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteAll(source: Source): Long {
  var totalBytesRead = 0L
  while (true) {
    val readCount: Long = source.read(buffer, Segment.SIZE.toLong())
    if (readCount == -1L) break
    totalBytesRead += readCount
    emitCompleteSegments()
  }
  return totalBytesRead
}

internal inline fun RealBufferedSink.commonWrite(source: Source, byteCount: Long): BufferedSink {
  var byteCount = byteCount
  while (byteCount > 0L) {
    val read = source.read(buffer, byteCount)
    if (read == -1L) throw EOFException()
    byteCount -= read
    emitCompleteSegments()
  }
  return this
}

internal inline fun RealBufferedSink.commonWriteByte(b: Int): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeByte(b)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteShort(s: Int): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeShort(s)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteShortLe(s: Int): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeShortLe(s)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteInt(i: Int): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeInt(i)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteIntLe(i: Int): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeIntLe(i)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteLong(v: Long): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeLong(v)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteLongLe(v: Long): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeLongLe(v)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteDecimalLong(v: Long): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeDecimalLong(v)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonWriteHexadecimalUnsignedLong(v: Long): BufferedSink {
  check(!closed) { "closed" }
  buffer.writeHexadecimalUnsignedLong(v)
  return emitCompleteSegments()
}

internal inline fun RealBufferedSink.commonEmitCompleteSegments(): BufferedSink {
  check(!closed) { "closed" }
  val byteCount = buffer.completeSegmentByteCount()
  if (byteCount > 0L) sink.write(buffer, byteCount)
  return this
}

internal inline fun RealBufferedSink.commonEmit(): BufferedSink {
  check(!closed) { "closed" }
  val byteCount = buffer.size
  if (byteCount > 0L) sink.write(buffer, byteCount)
  return this
}

internal inline fun RealBufferedSink.commonFlush() {
  check(!closed) { "closed" }
  if (buffer.size > 0L) {
    sink.write(buffer, buffer.size)
  }
  sink.flush()
}

internal inline fun RealBufferedSink.commonClose() {
  if (closed) return

  // Emit buffered data to the underlying sink. If this fails, we still need
  // to close the sink; otherwise we risk leaking resources.
  var thrown: Throwable? = null
  try {
    if (buffer.size > 0) {
      sink.write(buffer, buffer.size)
    }
  } catch (e: Throwable) {
    thrown = e
  }

  try {
    sink.close()
  } catch (e: Throwable) {
    if (thrown == null) thrown = e
  }

  closed = true

  if (thrown != null) throw thrown
}

internal inline fun RealBufferedSink.commonTimeout() = sink.timeout()

internal inline fun RealBufferedSink.commonToString() = "buffer($sink)"
