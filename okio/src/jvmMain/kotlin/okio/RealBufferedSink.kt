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

import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

internal class RealBufferedSink(
  @JvmField val sink: Sink
) : BufferedSink {
  @JvmField val bufferField = Buffer()
  @JvmField var closed: Boolean = false

  @Suppress("OVERRIDE_BY_INLINE") // Prevent internal code from calling the getter.
  override val buffer: Buffer
    inline get() = bufferField

  override fun buffer() = bufferField

  override fun write(source: Buffer, byteCount: Long) {
    check(!closed) { "closed" }
    buffer.write(source, byteCount)
    emitCompleteSegments()
  }

  override fun write(byteString: ByteString): BufferedSink {
    check(!closed) { "closed" }
    buffer.write(byteString)
    return emitCompleteSegments()
  }

  override fun writeUtf8(string: String): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeUtf8(string)
    return emitCompleteSegments()
  }

  override fun writeUtf8(string: String, beginIndex: Int, endIndex: Int): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeUtf8(string, beginIndex, endIndex)
    return emitCompleteSegments()
  }

  override fun writeUtf8CodePoint(codePoint: Int): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeUtf8CodePoint(codePoint)
    return emitCompleteSegments()
  }

  override fun writeString(string: String, charset: Charset): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeString(string, charset)
    return emitCompleteSegments()
  }

  override fun writeString(
    string: String,
    beginIndex: Int,
    endIndex: Int,
    charset: Charset
  ): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeString(string, beginIndex, endIndex, charset)
    return emitCompleteSegments()
  }

  override fun write(source: ByteArray): BufferedSink {
    check(!closed) { "closed" }
    buffer.write(source)
    return emitCompleteSegments()
  }

  override fun write(source: ByteArray, offset: Int, byteCount: Int): BufferedSink {
    check(!closed) { "closed" }
    buffer.write(source, offset, byteCount)
    return emitCompleteSegments()
  }

  override fun write(source: ByteBuffer): Int {
    check(!closed) { "closed" }
    val result = buffer.write(source)
    emitCompleteSegments()
    return result
  }

  override fun writeAll(source: Source): Long {
    var totalBytesRead = 0L
    while (true) {
      val readCount: Long = source.read(buffer, Segment.SIZE.toLong())
      if (readCount == -1L) break
      totalBytesRead += readCount
      emitCompleteSegments()
    }
    return totalBytesRead
  }

  override fun write(source: Source, byteCount: Long): BufferedSink {
    var byteCount = byteCount
    while (byteCount > 0L) {
      val read = source.read(buffer, byteCount)
      if (read == -1L) throw EOFException()
      byteCount -= read
      emitCompleteSegments()
    }
    return this
  }

  override fun writeByte(b: Int): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeByte(b)
    return emitCompleteSegments()
  }

  override fun writeShort(s: Int): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeShort(s)
    return emitCompleteSegments()
  }

  override fun writeShortLe(s: Int): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeShortLe(s)
    return emitCompleteSegments()
  }

  override fun writeInt(i: Int): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeInt(i)
    return emitCompleteSegments()
  }

  override fun writeIntLe(i: Int): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeIntLe(i)
    return emitCompleteSegments()
  }

  override fun writeLong(v: Long): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeLong(v)
    return emitCompleteSegments()
  }

  override fun writeLongLe(v: Long): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeLongLe(v)
    return emitCompleteSegments()
  }

  override fun writeDecimalLong(v: Long): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeDecimalLong(v)
    return emitCompleteSegments()
  }

  override fun writeHexadecimalUnsignedLong(v: Long): BufferedSink {
    check(!closed) { "closed" }
    buffer.writeHexadecimalUnsignedLong(v)
    return emitCompleteSegments()
  }

  override fun emitCompleteSegments(): BufferedSink {
    check(!closed) { "closed" }
    val byteCount = buffer.completeSegmentByteCount()
    if (byteCount > 0L) sink.write(buffer, byteCount)
    return this
  }

  override fun emit(): BufferedSink {
    check(!closed) { "closed" }
    val byteCount = buffer.size
    if (byteCount > 0L) sink.write(buffer, byteCount)
    return this
  }

  override fun outputStream(): OutputStream {
    return object : OutputStream() {
      override fun write(b: Int) {
        if (closed) throw IOException("closed")
        buffer.writeByte(b.toByte().toInt())
        emitCompleteSegments()
      }

      override fun write(data: ByteArray, offset: Int, byteCount: Int) {
        if (closed) throw IOException("closed")
        buffer.write(data, offset, byteCount)
        emitCompleteSegments()
      }

      override fun flush() {
        // For backwards compatibility, a flush() on a closed stream is a no-op.
        if (!closed) {
          this@RealBufferedSink.flush()
        }
      }

      override fun close() = this@RealBufferedSink.close()

      override fun toString() = "${this@RealBufferedSink}.outputStream()"
    }
  }

  override fun flush() {
    check(!closed) { "closed" }
    if (buffer.size > 0L) {
      sink.write(buffer, buffer.size)
    }
    sink.flush()
  }

  override fun isOpen() = !closed

  override fun close() {
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

  override fun timeout() = sink.timeout()

  override fun toString() = "buffer($sink)"
}
