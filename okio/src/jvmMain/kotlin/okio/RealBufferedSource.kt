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
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

internal class RealBufferedSource(
  @JvmField val source: Source
) : BufferedSource {
  @JvmField val bufferField = Buffer()
  @JvmField var closed: Boolean = false

  @Suppress("OVERRIDE_BY_INLINE") // Prevent internal code from calling the getter.
  override val buffer: Buffer
    inline get() = bufferField

  override fun buffer() = bufferField

  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }

    if (buffer.size == 0L) {
      val read = source.read(buffer, Segment.SIZE.toLong())
      if (read == -1L) return -1L
    }

    val toRead = minOf(byteCount, buffer.size)
    return buffer.read(sink, toRead)
  }

  override fun exhausted(): Boolean {
    check(!closed) { "closed" }
    return buffer.exhausted() && source.read(buffer, Segment.SIZE.toLong()) == -1L
  }

  override fun require(byteCount: Long) {
    if (!request(byteCount)) throw EOFException()
  }

  override fun request(byteCount: Long): Boolean {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    while (buffer.size < byteCount) {
      if (source.read(buffer, Segment.SIZE.toLong()) == -1L) return false
    }
    return true
  }

  override fun readByte(): Byte {
    require(1)
    return buffer.readByte()
  }

  override fun readByteString(): ByteString {
    buffer.writeAll(source)
    return buffer.readByteString()
  }

  override fun readByteString(byteCount: Long): ByteString {
    require(byteCount)
    return buffer.readByteString(byteCount)
  }

  override fun select(options: Options): Int {
    check(!closed) { "closed" }

    while (true) {
      val index = buffer.selectPrefix(options, selectTruncated = true)
      when (index) {
        -1 -> {
          return -1
        }
        -2 -> {
          // We need to grow the buffer. Do that, then try it all again.
          if (source.read(buffer, Segment.SIZE.toLong()) == -1L) return -1
        }
        else -> {
          // We matched a full byte string: consume it and return it.
          val selectedSize = options.byteStrings[index].size
          buffer.skip(selectedSize.toLong())
          return index
        }
      }
    }
  }

  override fun readByteArray(): ByteArray {
    buffer.writeAll(source)
    return buffer.readByteArray()
  }

  override fun readByteArray(byteCount: Long): ByteArray {
    require(byteCount)
    return buffer.readByteArray(byteCount)
  }

  override fun read(sink: ByteArray) = read(sink, 0, sink.size)

  override fun readFully(sink: ByteArray) {
    try {
      require(sink.size.toLong())
    } catch (e: EOFException) {
      // The underlying source is exhausted. Copy the bytes we got before rethrowing.
      var offset = 0
      while (buffer.size > 0L) {
        val read = buffer.read(sink, offset, buffer.size.toInt())
        if (read == -1) throw AssertionError()
        offset += read
      }
      throw e
    }

    buffer.readFully(sink)
  }

  override fun read(sink: ByteArray, offset: Int, byteCount: Int): Int {
    checkOffsetAndCount(sink.size.toLong(), offset.toLong(), byteCount.toLong())

    if (buffer.size == 0L) {
      val read = source.read(buffer, Segment.SIZE.toLong())
      if (read == -1L) return -1
    }

    val toRead = minOf(byteCount, buffer.size).toInt()
    return buffer.read(sink, offset, toRead)
  }

  override fun read(sink: ByteBuffer): Int {
    if (buffer.size == 0L) {
      val read = source.read(buffer, Segment.SIZE.toLong())
      if (read == -1L) return -1
    }

    return buffer.read(sink)
  }

  override fun readFully(sink: Buffer, byteCount: Long) {
    try {
      require(byteCount)
    } catch (e: EOFException) {
      // The underlying source is exhausted. Copy the bytes we got before rethrowing.
      sink.writeAll(buffer)
      throw e
    }

    buffer.readFully(sink, byteCount)
  }

  override fun readAll(sink: Sink): Long {
    var totalBytesWritten: Long = 0
    while (source.read(buffer, Segment.SIZE.toLong()) != -1L) {
      val emitByteCount = buffer.completeSegmentByteCount()
      if (emitByteCount > 0L) {
        totalBytesWritten += emitByteCount
        sink.write(buffer, emitByteCount)
      }
    }
    if (buffer.size > 0L) {
      totalBytesWritten += buffer.size
      sink.write(buffer, buffer.size)
    }
    return totalBytesWritten
  }

  override fun readUtf8(): String {
    buffer.writeAll(source)
    return buffer.readUtf8()
  }

  override fun readUtf8(byteCount: Long): String {
    require(byteCount)
    return buffer.readUtf8(byteCount)
  }

  override fun readString(charset: Charset): String {
    buffer.writeAll(source)
    return buffer.readString(charset)
  }

  override fun readString(byteCount: Long, charset: Charset): String {
    require(byteCount)
    return buffer.readString(byteCount, charset)
  }

  override fun readUtf8Line(): String? {
    val newline = indexOf('\n'.toByte())

    return if (newline == -1L) {
      if (buffer.size != 0L) {
        readUtf8(buffer.size)
      } else {
        null
      }
    } else {
      buffer.readUtf8Line(newline)
    }
  }

  override fun readUtf8LineStrict() = readUtf8LineStrict(Long.MAX_VALUE)

  override fun readUtf8LineStrict(limit: Long): String {
    require(limit >= 0) { "limit < 0: $limit" }
    val scanLength = if (limit == Long.MAX_VALUE) Long.MAX_VALUE else limit + 1
    val newline = indexOf('\n'.toByte(), 0, scanLength)
    if (newline != -1L) return buffer.readUtf8Line(newline)
    if (scanLength < Long.MAX_VALUE &&
        request(scanLength) && buffer[scanLength - 1] == '\r'.toByte() &&
        request(scanLength + 1) && buffer[scanLength] == '\n'.toByte()) {
      return buffer.readUtf8Line(scanLength) // The line was 'limit' UTF-8 bytes followed by \r\n.
    }
    val data = Buffer()
    buffer.copyTo(data, 0, minOf(32, buffer.size))
    throw EOFException("\\n not found: limit=" + minOf(buffer.size, limit) +
      " content=" + data.readByteString().hex() + '…'.toString())
  }

  override fun readUtf8CodePoint(): Int {
    require(1)

    val b0 = buffer[0].toInt()
    when {
      b0 and 0xe0 == 0xc0 -> require(2)
      b0 and 0xf0 == 0xe0 -> require(3)
      b0 and 0xf8 == 0xf0 -> require(4)
    }

    return buffer.readUtf8CodePoint()
  }

  override fun readShort(): Short {
    require(2)
    return buffer.readShort()
  }

  override fun readShortLe(): Short {
    require(2)
    return buffer.readShortLe()
  }

  override fun readInt(): Int {
    require(4)
    return buffer.readInt()
  }

  override fun readIntLe(): Int {
    require(4)
    return buffer.readIntLe()
  }

  override fun readLong(): Long {
    require(8)
    return buffer.readLong()
  }

  override fun readLongLe(): Long {
    require(8)
    return buffer.readLongLe()
  }

  override fun readDecimalLong(): Long {
    require(1)

    var pos = 0L
    while (request(pos + 1)) {
      val b = buffer[pos]
      if ((b < '0'.toByte() || b > '9'.toByte()) && (pos != 0L || b != '-'.toByte())) {
        // Non-digit, or non-leading negative sign.
        if (pos == 0L) {
          throw NumberFormatException(String.format(
              "Expected leading [0-9] or '-' character but was %#x", b))
        }
        break
      }
      pos++
    }

    return buffer.readDecimalLong()
  }

  override fun readHexadecimalUnsignedLong(): Long {
    require(1)

    var pos = 0
    while (request((pos + 1).toLong())) {
      val b = buffer[pos.toLong()]
      if ((b < '0'.toByte() || b > '9'.toByte()) &&
          (b < 'a'.toByte() || b > 'f'.toByte()) &&
          (b < 'A'.toByte() || b > 'F'.toByte())) {
        // Non-digit, or non-leading negative sign.
        if (pos == 0) {
          throw NumberFormatException(String.format(
              "Expected leading [0-9a-fA-F] character but was %#x", b))
        }
        break
      }
      pos++
    }

    return buffer.readHexadecimalUnsignedLong()
  }

  override fun skip(byteCount: Long) {
    var byteCount = byteCount
    check(!closed) { "closed" }
    while (byteCount > 0) {
      if (buffer.size == 0L && source.read(buffer, Segment.SIZE.toLong()) == -1L) {
        throw EOFException()
      }
      val toSkip = minOf(byteCount, buffer.size)
      buffer.skip(toSkip)
      byteCount -= toSkip
    }
  }

  override fun indexOf(b: Byte) = indexOf(b, 0L, Long.MAX_VALUE)

  override fun indexOf(b: Byte, fromIndex: Long) = indexOf(b, fromIndex, Long.MAX_VALUE)

  override fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long {
    var fromIndex = fromIndex
    check(!closed) { "closed" }
    require(fromIndex in 0L..toIndex) { "fromIndex=$fromIndex toIndex=$toIndex" }

    while (fromIndex < toIndex) {
      val result = buffer.indexOf(b, fromIndex, toIndex)
      if (result != -1L) return result

      // The byte wasn't in the buffer. Give up if we've already reached our target size or if the
      // underlying stream is exhausted.
      val lastBufferSize = buffer.size
      if (lastBufferSize >= toIndex || source.read(buffer, Segment.SIZE.toLong()) == -1L) return -1L

      // Continue the search from where we left off.
      fromIndex = maxOf(fromIndex, lastBufferSize)
    }
    return -1L
  }

  override fun indexOf(bytes: ByteString) = indexOf(bytes, 0L)

  override fun indexOf(bytes: ByteString, fromIndex: Long): Long {
    var fromIndex = fromIndex
    check(!closed) { "closed" }

    while (true) {
      val result = buffer.indexOf(bytes, fromIndex)
      if (result != -1L) return result

      val lastBufferSize = buffer.size
      if (source.read(buffer, Segment.SIZE.toLong()) == -1L) return -1L

      // Keep searching, picking up from where we left off.
      fromIndex = maxOf(fromIndex, lastBufferSize - bytes.size + 1)
    }
  }

  override fun indexOfElement(targetBytes: ByteString) = indexOfElement(targetBytes, 0L)

  override fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long {
    var fromIndex = fromIndex
    check(!closed) { "closed" }

    while (true) {
      val result = buffer.indexOfElement(targetBytes, fromIndex)
      if (result != -1L) return result

      val lastBufferSize = buffer.size
      if (source.read(buffer, Segment.SIZE.toLong()) == -1L) return -1L

      // Keep searching, picking up from where we left off.
      fromIndex = maxOf(fromIndex, lastBufferSize)
    }
  }

  override fun rangeEquals(offset: Long, bytes: ByteString) = rangeEquals(offset, bytes, 0,
      bytes.size)

  override fun rangeEquals(
    offset: Long,
    bytes: ByteString,
    bytesOffset: Int,
    byteCount: Int
  ): Boolean {
    check(!closed) { "closed" }

    if (offset < 0L ||
        bytesOffset < 0 ||
        byteCount < 0 ||
        bytes.size - bytesOffset < byteCount) {
      return false
    }
    for (i in 0 until byteCount) {
      val bufferOffset = offset + i
      if (!request(bufferOffset + 1)) return false
      if (buffer[bufferOffset] != bytes[bytesOffset + i]) return false
    }
    return true
  }

  override fun peek(): BufferedSource {
    return PeekSource(this).buffer()
  }

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
    }
  }

  override fun isOpen() = !closed

  override fun close() {
    if (closed) return
    closed = true
    source.close()
    buffer.clear()
  }

  override fun timeout() = source.timeout()

  override fun toString() = "buffer($source)"
}
