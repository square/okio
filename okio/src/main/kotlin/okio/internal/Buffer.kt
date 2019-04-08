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
package okio.internal

import okio.Buffer
import okio.ByteString
import okio.EOFException
import okio.Segment
import okio.SegmentPool
import okio.checkOffsetAndCount

// TODO Kotlin's expect classes can't have default implementations, so platform implementations
// have to call these functions. Remove all this nonsense when expect class allow actual code.

internal fun Buffer.commonCopyTo(
  out: Buffer,
  offset: Long,
  byteCount: Long
): Buffer {
  var offset = offset
  var byteCount = byteCount
  checkOffsetAndCount(size, offset, byteCount)
  if (byteCount == 0L) return this

  out.size += byteCount

  // Skip segments that we aren't copying from.
  var s = head
  while (offset >= s!!.limit - s.pos) {
    offset -= (s.limit - s.pos).toLong()
    s = s.next
  }

  // Copy one segment at a time.
  while (byteCount > 0L) {
    val copy = s!!.sharedCopy()
    copy.pos += offset.toInt()
    copy.limit = minOf(copy.pos + byteCount.toInt(), copy.limit)
    if (out.head == null) {
      copy.prev = copy
      copy.next = copy.prev
      out.head = copy.next
    } else {
      out.head!!.prev!!.push(copy)
    }
    byteCount -= (copy.limit - copy.pos).toLong()
    offset = 0L
    s = s.next
  }

  return this
}

internal fun Buffer.commonReadByte(): Byte {
  if (size == 0L) throw EOFException()

  val segment = head!!
  var pos = segment.pos
  val limit = segment.limit

  val data = segment.data
  val b = data[pos++]
  size -= 1L

  if (pos == limit) {
    head = segment.pop()
    SegmentPool.recycle(segment)
  } else {
    segment.pos = pos
  }

  return b
}

internal fun Buffer.commonGet(pos: Long): Byte {
  checkOffsetAndCount(size, pos, 1L)
  seek(pos) { s, offset ->
    return s!!.data[(s.pos + pos - offset).toInt()]
  }
}

/**
 * Invoke `lambda` with the segment and offset at `fromIndex`. Searches from the front or the back
 * depending on what's closer to `fromIndex`.
 */
internal inline fun <T> Buffer.seek(
  fromIndex: Long,
  lambda: (Segment?, Long) -> T
): T {
  var s: Segment = head ?: return lambda(null, -1L)

  if (size - fromIndex < fromIndex) {
    // We're scanning in the back half of this buffer. Find the segment starting at the back.
    var offset = size
    while (offset > fromIndex) {
      s = s.prev!!
      offset -= (s.limit - s.pos).toLong()
    }
    return lambda(s, offset)
  } else {
    // We're scanning in the front half of this buffer. Find the segment starting at the front.
    var offset = 0L
    while (true) {
      val nextOffset = offset + (s.limit - s.pos)
      if (nextOffset > fromIndex) break
      s = s.next!!
      offset = nextOffset
    }
    return lambda(s, offset)
  }
}

internal fun Buffer.commonWrite(byteString: ByteString): Buffer {
  byteString.write(this)
  return this
}

internal fun Buffer.commonWritableSegment(minimumCapacity: Int): Segment {
  require(minimumCapacity >= 1 && minimumCapacity <= Segment.SIZE) { "unexpected capacity" }

  if (head == null) {
    val result = SegmentPool.take() // Acquire a first segment.
    head = result
    result.prev = result
    result.next = result
    return result
  }

  var tail = head!!.prev
  if (tail!!.limit + minimumCapacity > Segment.SIZE || !tail.owner) {
    tail = tail.push(SegmentPool.take()) // Append a new empty segment to fill up.
  }
  return tail
}

internal fun Buffer.commonWrite(source: ByteArray) = write(source, 0, source.size)

internal fun Buffer.commonWrite(
  source: ByteArray,
  offset: Int,
  byteCount: Int
): Buffer {
  var offset = offset
  checkOffsetAndCount(source.size.toLong(), offset.toLong(), byteCount.toLong())

  val limit = offset + byteCount
  while (offset < limit) {
    val tail = writableSegment(1)

    val toCopy = minOf(limit - offset, Segment.SIZE - tail.limit)
    source.copyInto(
        destination = tail.data,
        destinationOffset = tail.limit,
        startIndex = offset,
        endIndex = offset + toCopy
    )

    offset += toCopy
    tail.limit += toCopy
  }

  size += byteCount.toLong()
  return this
}


internal fun Buffer.commonReadByteArray() = readByteArray(size)

internal fun Buffer.commonReadByteArray(byteCount: Long): ByteArray {
  require(byteCount >= 0 && byteCount <= Int.MAX_VALUE) { "byteCount: $byteCount" }
  if (size < byteCount) throw EOFException()

  val result = ByteArray(byteCount.toInt())
  readFully(result)
  return result
}

internal fun Buffer.commonRead(sink: ByteArray) = read(sink, 0, sink.size)

internal fun Buffer.commonReadFully(sink: ByteArray) {
  var offset = 0
  while (offset < sink.size) {
    val read = read(sink, offset, sink.size - offset)
    if (read == -1) throw EOFException()
    offset += read
  }
}

internal fun Buffer.commonRead(sink: ByteArray, offset: Int, byteCount: Int): Int {
  checkOffsetAndCount(sink.size.toLong(), offset.toLong(), byteCount.toLong())

  val s = head ?: return -1
  val toCopy = minOf(byteCount, s.limit - s.pos)
  s.data.copyInto(
      destination = sink, destinationOffset = offset, startIndex = s.pos, endIndex = s.pos + toCopy
  )

  s.pos += toCopy
  size -= toCopy.toLong()

  if (s.pos == s.limit) {
    head = s.pop()
    SegmentPool.recycle(s)
  }

  return toCopy
}

internal fun Buffer.commonReadByteString(): ByteString = ByteString(readByteArray())

internal fun Buffer.commonReadByteString(byteCount: Long) = ByteString(readByteArray(byteCount))
