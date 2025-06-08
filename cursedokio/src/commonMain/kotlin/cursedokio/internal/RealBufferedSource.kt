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

// TODO move to RealBufferedSource class: https://youtrack.jetbrains.com/issue/KT-20427
@file:Suppress("NOTHING_TO_INLINE")

@file:JvmName("-RealBufferedSource") // A leading '-' hides this class from Java.

package cursedokio.internal

import cursedokio.Buffer
import cursedokio.BufferedSource
import cursedokio.ByteString
import cursedokio.EOFException
import cursedokio.Options
import cursedokio.PeekSource
import cursedokio.RealBufferedSource
import cursedokio.Segment
import cursedokio.Sink
import cursedokio.buffer
import cursedokio.checkOffsetAndCount
import cursedokio.minOf

internal suspend inline fun RealBufferedSource.commonRead(sink: Buffer, byteCount: Long): Long {
  require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
  check(!closed) { "closed" }

  if (buffer.size == 0L) {
    if (byteCount == 0L) return 0L
    val read = source.read(buffer, Segment.SIZE.toLong())
    if (read == -1L) return -1L
  }

  val toRead = minOf(byteCount, buffer.size)
  return buffer.read(sink, toRead)
}

internal suspend inline fun RealBufferedSource.commonExhausted(): Boolean {
  check(!closed) { "closed" }
  return buffer.exhausted() && source.read(buffer, Segment.SIZE.toLong()) == -1L
}

internal suspend inline fun RealBufferedSource.commonRequire(byteCount: Long) {
  if (!request(byteCount)) throw EOFException()
}

internal suspend inline fun RealBufferedSource.commonRequest(byteCount: Long): Boolean {
  require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
  check(!closed) { "closed" }
  while (buffer.size < byteCount) {
    if (source.read(buffer, Segment.SIZE.toLong()) == -1L) return false
  }
  return true
}

internal suspend inline fun RealBufferedSource.commonReadByte(): Byte {
  require(1)
  return buffer.readByte()
}

internal suspend inline fun RealBufferedSource.commonReadByteString(): ByteString {
  buffer.writeAll(source)
  return buffer.readByteString()
}

internal suspend inline fun RealBufferedSource.commonReadByteString(byteCount: Long): ByteString {
  require(byteCount)
  return buffer.readByteString(byteCount)
}

internal suspend inline fun RealBufferedSource.commonSelect(options: Options): Int {
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

internal suspend inline fun RealBufferedSource.commonReadByteArray(): ByteArray {
  buffer.writeAll(source)
  return buffer.readByteArray()
}

internal suspend inline fun RealBufferedSource.commonReadByteArray(byteCount: Long): ByteArray {
  require(byteCount)
  return buffer.readByteArray(byteCount)
}

internal suspend inline fun RealBufferedSource.commonReadFully(sink: ByteArray) {
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

internal suspend inline fun RealBufferedSource.commonRead(sink: ByteArray, offset: Int, byteCount: Int): Int {
  checkOffsetAndCount(sink.size.toLong(), offset.toLong(), byteCount.toLong())

  if (buffer.size == 0L) {
    if (byteCount == 0) return 0
    val read = source.read(buffer, Segment.SIZE.toLong())
    if (read == -1L) return -1
  }

  val toRead = cursedokio.minOf(byteCount, buffer.size).toInt()
  return buffer.read(sink, offset, toRead)
}

internal suspend inline fun RealBufferedSource.commonReadFully(sink: Buffer, byteCount: Long) {
  try {
    require(byteCount)
  } catch (e: EOFException) {
    // The underlying source is exhausted. Copy the bytes we got before rethrowing.
    sink.writeAll(buffer)
    throw e
  }

  buffer.readFully(sink, byteCount)
}

internal suspend inline fun RealBufferedSource.commonReadAll(sink: Sink): Long {
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

internal suspend inline fun RealBufferedSource.commonReadUtf8(): String {
  buffer.writeAll(source)
  return buffer.readUtf8()
}

internal suspend inline fun RealBufferedSource.commonReadUtf8(byteCount: Long): String {
  require(byteCount)
  return buffer.readUtf8(byteCount)
}

internal suspend inline fun RealBufferedSource.commonReadUtf8Line(): String? {
  val newline = indexOf('\n'.code.toByte())

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

internal suspend inline fun RealBufferedSource.commonReadUtf8LineStrict(limit: Long): String {
  require(limit >= 0) { "limit < 0: $limit" }
  val scanLength = if (limit == Long.MAX_VALUE) Long.MAX_VALUE else limit + 1
  val newline = indexOf('\n'.code.toByte(), 0, scanLength)
  if (newline != -1L) return buffer.readUtf8Line(newline)
  if (scanLength < Long.MAX_VALUE &&
    request(scanLength) && buffer[scanLength - 1] == '\r'.code.toByte() &&
    request(scanLength + 1) && buffer[scanLength] == '\n'.code.toByte()
  ) {
    return buffer.readUtf8Line(scanLength) // The line was 'limit' UTF-8 bytes followed by \r\n.
  }
  val data = Buffer()
  buffer.copyTo(data, 0, cursedokio.minOf(32, buffer.size))
  throw EOFException(
    "\\n not found: limit=" + minOf(buffer.size, limit) +
      " content=" + data.readByteString().hex() + '…'.toString(),
  )
}

internal suspend inline fun RealBufferedSource.commonReadUtf8CodePoint(): Int {
  require(1)

  val b0 = buffer[0].toInt()
  when {
    b0 and 0xe0 == 0xc0 -> require(2)
    b0 and 0xf0 == 0xe0 -> require(3)
    b0 and 0xf8 == 0xf0 -> require(4)
  }

  return buffer.readUtf8CodePoint()
}

internal suspend inline fun RealBufferedSource.commonReadShort(): Short {
  require(2)
  return buffer.readShort()
}

internal suspend inline fun RealBufferedSource.commonReadShortLe(): Short {
  require(2)
  return buffer.readShortLe()
}

internal suspend inline fun RealBufferedSource.commonReadInt(): Int {
  require(4)
  return buffer.readInt()
}

internal suspend inline fun RealBufferedSource.commonReadIntLe(): Int {
  require(4)
  return buffer.readIntLe()
}

internal suspend inline fun RealBufferedSource.commonReadLong(): Long {
  require(8)
  return buffer.readLong()
}

internal suspend inline fun RealBufferedSource.commonReadLongLe(): Long {
  require(8)
  return buffer.readLongLe()
}

internal suspend inline fun RealBufferedSource.commonReadDecimalLong(): Long {
  require(1)

  var pos = 0L
  while (request(pos + 1)) {
    val b = buffer[pos]
    if ((b < '0'.code.toByte() || b > '9'.code.toByte()) && (pos != 0L || b != '-'.code.toByte())) {
      // Non-digit, or non-leading negative sign.
      if (pos == 0L) {
        throw NumberFormatException("Expected a digit or '-' but was 0x${b.toString(16)}")
      }
      break
    }
    pos++
  }

  return buffer.readDecimalLong()
}

internal suspend inline fun RealBufferedSource.commonReadHexadecimalUnsignedLong(): Long {
  require(1)

  var pos = 0
  while (request((pos + 1).toLong())) {
    val b = buffer[pos.toLong()]
    if ((b < '0'.code.toByte() || b > '9'.code.toByte()) &&
      (b < 'a'.code.toByte() || b > 'f'.code.toByte()) &&
      (b < 'A'.code.toByte() || b > 'F'.code.toByte())
    ) {
      // Non-digit, or non-leading negative sign.
      if (pos == 0) {
        throw NumberFormatException("Expected leading [0-9a-fA-F] character but was 0x${b.toString(16)}")
      }
      break
    }
    pos++
  }

  return buffer.readHexadecimalUnsignedLong()
}

internal suspend inline fun RealBufferedSource.commonSkip(byteCount: Long) {
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

internal suspend inline fun RealBufferedSource.commonIndexOf(b: Byte, fromIndex: Long, toIndex: Long): Long {
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

internal suspend fun RealBufferedSource.commonIndexOf(
  bytes: ByteString,
  bytesOffset: Int = 0,
  byteCount: Int = bytes.size,
  fromIndex: Long,
  toIndex: Long = Long.MAX_VALUE,
): Long {
  checkOffsetAndCount(bytes.size.toLong(), bytesOffset.toLong(), byteCount.toLong())

  var fromIndex = fromIndex
  check(!closed) { "closed" }

  while (true) {
    val result = buffer.commonIndexOf(
      bytes = bytes,
      bytesOffset = bytesOffset,
      byteCount = byteCount,
      fromIndex = fromIndex,
      toIndex = toIndex,
    )
    if (result != -1L) return result

    val lastBufferSize = buffer.size
    val nextFromIndex = lastBufferSize - byteCount + 1
    if (nextFromIndex >= toIndex) return -1L
    if (
      !buffer.isMatchPossibleByExpandingBuffer(
        bytes = bytes,
        bytesOffset = bytesOffset,
        byteCount = byteCount,
        fromIndex = fromIndex,
        toIndex = toIndex,
      )
    ) {
      return -1L
    }
    if (source.read(buffer, Segment.SIZE.toLong()) == -1L) return -1L

    // Keep searching, picking up from where we left off.
    fromIndex = maxOf(fromIndex, nextFromIndex)
  }
}

/**
 * Returns true if loading more data could result in an `indexOf` match.
 *
 * This function's utility is avoiding potentially-slow `read` calls that cannot impact the result
 * of an `indexOf` call. For example, consider this situation:
 *
 * ```
 * val source = ...
 * source.indexOf("hello", fromIndex = 0, toIndex = 4)
 * ```
 *
 * If the source's loaded content is the string "shell", it is necessary to load more data because
 * if the next loaded byte is 'o' then the result will be 1. But if the source's loaded content is
 * 'look', we know the result is -1 without loading more data.
 */
private suspend fun Buffer.isMatchPossibleByExpandingBuffer(
  bytes: ByteString,
  bytesOffset: Int,
  byteCount: Int,
  fromIndex: Long,
  toIndex: Long,
): Boolean {
  // Load new data if the match could come entirely in that new data.
  if (size < toIndex) return true

  // Load new data if a prefix of 'bytes' matches a suffix of 'buffer'.
  val begin = maxOf(1, size - toIndex + 1).toInt()
  val limit = minOf(byteCount, size - fromIndex + 1).toInt()
  for (i in limit - 1 downTo begin) {
    if (rangeEquals(size - i, bytes, bytesOffset, i)) {
      return true
    }
  }

  // No matter what we load, we won't find a match.
  return false
}

internal suspend inline fun RealBufferedSource.commonIndexOfElement(
  targetBytes: ByteString,
  fromIndex: Long,
): Long {
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

internal suspend inline fun RealBufferedSource.commonRangeEquals(
  offset: Long,
  bytes: ByteString,
  bytesOffset: Int,
  byteCount: Int,
): Boolean {
  check(!closed) { "closed" }

  if (byteCount < 0) return false
  if (offset < 0) return false
  if (bytesOffset < 0 || bytesOffset + byteCount > bytes.size) return false
  if (byteCount == 0) return true

  return commonIndexOf(
    bytes = bytes,
    bytesOffset = bytesOffset,
    byteCount = byteCount,
    fromIndex = offset,
    toIndex = offset + 1,
  ) != -1L
}

internal inline fun RealBufferedSource.commonPeek(): BufferedSource {
  return PeekSource(this).buffer()
}

internal suspend inline fun RealBufferedSource.commonClose() {
  if (closed) return
  closed = true
  source.close()
  buffer.clear()
}

internal inline fun RealBufferedSource.commonTimeout() = source.timeout()

internal inline fun RealBufferedSource.commonToString() = "buffer($source)"
