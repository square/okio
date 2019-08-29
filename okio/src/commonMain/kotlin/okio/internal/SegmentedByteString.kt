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

// TODO move to SegmentedByteString class: https://youtrack.jetbrains.com/issue/KT-20427
@file:Suppress("NOTHING_TO_INLINE")

package okio.internal

import okio.Buffer
import okio.ByteString
import okio.Segment
import okio.SegmentedByteString
import okio.arrayRangeEquals
import okio.checkOffsetAndCount

internal fun IntArray.binarySearch(value: Int, fromIndex: Int, toIndex: Int): Int {
  var left = fromIndex
  var right = toIndex - 1

  while (left <= right) {
    val mid = (left + right) ushr 1 // protect from overflow
    val midVal = this[mid]

    when {
      midVal < value -> left = mid + 1
      midVal > value -> right = mid - 1
      else -> return mid
    }
  }

  // no exact match, return negative of where it should match
  return -left - 1
}

/** Returns the index of the segment that contains the byte at `pos`.  */
internal fun SegmentedByteString.segment(pos: Int): Int {
  // Search for (pos + 1) instead of (pos) because the directory holds sizes, not indexes.
  val i = directory.binarySearch(pos + 1, 0, segments.size)
  return if (i >= 0) i else i.inv() // If i is negative, bitflip to get the insert position.
}

/** Processes all segments, invoking `action` with the ByteArray and range of valid data. */
internal inline fun SegmentedByteString.forEachSegment(
  action: (data: ByteArray, offset: Int, byteCount: Int) -> Unit
) {
  val segmentCount = segments.size
  var s = 0
  var pos = 0
  while (s < segmentCount) {
    val segmentPos = directory[segmentCount + s]
    val nextSegmentOffset = directory[s]

    action(segments[s], segmentPos, nextSegmentOffset - pos)
    pos = nextSegmentOffset
    s++
  }
}

/**
 * Processes the segments between `beginIndex` and `endIndex`, invoking `action` with the ByteArray
 * and range of the valid data.
 */
private inline fun SegmentedByteString.forEachSegment(
  beginIndex: Int,
  endIndex: Int,
  action: (data: ByteArray, offset: Int, byteCount: Int) -> Unit
) {
  var s = segment(beginIndex)
  var pos = beginIndex
  while (pos < endIndex) {
    val segmentOffset = if (s == 0) 0 else directory[s - 1]
    val segmentSize = directory[s] - segmentOffset
    val segmentPos = directory[segments.size + s]

    val byteCount = minOf(endIndex, segmentOffset + segmentSize) - pos
    val offset = segmentPos + (pos - segmentOffset)
    action(segments[s], offset, byteCount)
    pos += byteCount
    s++
  }
}

// TODO Kotlin's expect classes can't have default implementations, so platform implementations
// have to call these functions. Remove all this nonsense when expect class allow actual code.

internal inline fun SegmentedByteString.commonSubstring(beginIndex: Int, endIndex: Int): ByteString {
  require(beginIndex >= 0) { "beginIndex=$beginIndex < 0" }
  require(endIndex <= size) { "endIndex=$endIndex > length($size)" }

  val subLen = endIndex - beginIndex
  require(subLen >= 0) { "endIndex=$endIndex < beginIndex=$beginIndex" }

  when {
    beginIndex == 0 && endIndex == size -> return this
    beginIndex == endIndex -> return ByteString.EMPTY
  }

  val beginSegment = segment(beginIndex) // First segment to include
  val endSegment = segment(endIndex - 1) // Last segment to include

  val newSegments = segments.copyOfRange(beginSegment, endSegment + 1)
  val newDirectory = IntArray(newSegments.size * 2)
  var index = 0
  for (s in beginSegment..endSegment) {
    newDirectory[index] = minOf(directory[s] - beginIndex, subLen)
    newDirectory[index++ + newSegments.size] = directory[s + segments.size]
  }

  // Set the new position of the first segment
  val segmentOffset = if (beginSegment == 0) 0 else directory[beginSegment - 1]
  newDirectory[newSegments.size] += beginIndex - segmentOffset

  return SegmentedByteString(newSegments, newDirectory)
}

internal inline fun SegmentedByteString.commonInternalGet(pos: Int): Byte {
  checkOffsetAndCount(directory[segments.size - 1].toLong(), pos.toLong(), 1)
  val segment = segment(pos)
  val segmentOffset = if (segment == 0) 0 else directory[segment - 1]
  val segmentPos = directory[segment + segments.size]
  return segments[segment][pos - segmentOffset + segmentPos]
}

internal inline fun SegmentedByteString.commonGetSize() = directory[segments.size - 1]

internal inline fun SegmentedByteString.commonToByteArray(): ByteArray {
  val result = ByteArray(size)
  var resultPos = 0
  forEachSegment { data, offset, byteCount ->
    data.copyInto(result, destinationOffset = resultPos, startIndex = offset,
      endIndex = offset + byteCount)
    resultPos += byteCount
  }
  return result
}

internal inline fun SegmentedByteString.commonWrite(buffer: Buffer, offset: Int, byteCount: Int) {
  forEachSegment(offset, offset + byteCount) { data, offset, byteCount ->
    val segment = Segment(data, offset, offset + byteCount, true, false)
    if (buffer.head == null) {
      segment.prev = segment
      segment.next = segment.prev
      buffer.head = segment.next
    } else {
      buffer.head!!.prev!!.push(segment)
    }
  }
  buffer.size += size
}

internal inline fun SegmentedByteString.commonRangeEquals(
  offset: Int,
  other: ByteString,
  otherOffset: Int,
  byteCount: Int
): Boolean {
  if (offset < 0 || offset > size - byteCount) return false
  // Go segment-by-segment through this, passing arrays to other's rangeEquals().
  var otherOffset = otherOffset
  forEachSegment(offset, offset + byteCount) { data, offset, byteCount ->
    if (!other.rangeEquals(otherOffset, data, offset, byteCount)) return false
    otherOffset += byteCount
  }
  return true
}

internal inline fun SegmentedByteString.commonRangeEquals(
  offset: Int,
  other: ByteArray,
  otherOffset: Int,
  byteCount: Int
): Boolean {
  if (offset < 0 || offset > size - byteCount ||
    otherOffset < 0 || otherOffset > other.size - byteCount) {
    return false
  }
  // Go segment-by-segment through this, comparing ranges of arrays.
  var otherOffset = otherOffset
  forEachSegment(offset, offset + byteCount) { data, offset, byteCount ->
    if (!arrayRangeEquals(data, offset, other, otherOffset, byteCount)) return false
    otherOffset += byteCount
  }
  return true
}

internal inline fun SegmentedByteString.commonEquals(other: Any?): Boolean {
  return when {
    other === this -> true
    other is ByteString -> other.size == size && rangeEquals(0, other, 0, size)
    else -> false
  }
}

internal inline fun SegmentedByteString.commonHashCode(): Int {
  var result = hashCode
  if (result != 0) return result

  // Equivalent to Arrays.hashCode(toByteArray()).
  result = 1
  forEachSegment { data, offset, byteCount ->
    var i = offset
    val limit = offset + byteCount
    while (i < limit) {
      result = 31 * result + data[i]
      i++
    }
  }
  hashCode = result
  return result
}
