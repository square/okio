/*
 * Copyright (C) 2020 Square, Inc.
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

import kotlin.math.min
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.plus
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSMutableData
import platform.Foundation.create
import platform.darwin.NSInteger
import platform.darwin.NSUInteger
import platform.posix.memcpy

fun Buffer.write(
  source: NSData,
  offset: Int = 0,
  byteCount: Int = source.length.toInt()
): Buffer = apply {
  var offset = offset
  checkOffsetAndCount(source.length.toLong(), offset.toLong(), byteCount.toLong())

  val limit = offset + byteCount
  while (offset < limit) {
    val tail = writableSegment(1)

    val toCopy = minOf(limit - offset, Segment.SIZE - tail.limit)
    tail.data.usePinned { pinned ->
      memcpy(
        __dst = pinned.addressOf(tail.limit),
        __src = (source.bytes as CPointer<ByteVarOf<*>>) + offset,
        __n = toCopy.toULong()
      )
    }

    offset += toCopy
    tail.limit += toCopy
  }

  size += byteCount.toLong()
}

fun Buffer.readNSData(byteCount: NSUInteger = size.toULong()): NSData {
  require(byteCount >= 0uL && byteCount <= NSUInteger.MAX_VALUE) { "byteCount: $byteCount" }
  if (size.toULong() < byteCount) throw EOFException()

  val result = NSMutableData.create(length = byteCount)
    ?: throw IOException("Failed to create NSMutableData of length $byteCount")
  readFully(result)
  return result
}

fun Buffer.read(
  sink: NSMutableData,
  offset: NSUInteger = 0uL,
  byteCount: NSUInteger = size.toULong()
): NSUInteger {
  checkOffsetAndCount(sink.length, offset, byteCount)

  val offset = offset.toLong()
  val s = head ?: return (-1).toULong()
  val toCopy = min(byteCount, (s.limit - s.pos).toULong())
  s.data.usePinned { pinned ->
    memcpy(
      __dst = (sink.bytes as CPointer<ByteVarOf<*>>) + offset,
      __src = pinned.addressOf(s.pos),
      __n = toCopy
    )
  }

  s.pos += toCopy.toInt()
  size -= toCopy.toLong()

  if (s.pos == s.limit) {
    head = s.pop()
    SegmentPool.recycle(s)
  }

  return toCopy
}

internal fun checkOffsetAndCount(size: ULong, offset: ULong, byteCount: ULong) {
  if (offset or byteCount < 0uL || offset > size || size - offset < byteCount) {
    throw ArrayIndexOutOfBoundsException("size=$size offset=$offset byteCount=$byteCount")
  }
}

fun Buffer.readFully(sink: NSMutableData) {
  val size = sink.length
  var offset = 0uL
  while (offset < size) {
    val read = read(sink, offset, size - offset)
    if (read == (-1).toULong()) throw EOFException()
    offset += read
  }
}
