/*
 * Copyright (C) 2015 Square, Inc.
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

import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * An immutable byte string composed of segments of byte arrays. This class exists to implement
 * efficient snapshots of buffers. It is implemented as an array of segments, plus a directory in
 * two halves that describes how the segments compose this byte string.
 *
 * The first half of the directory is the cumulative byte count covered by each segment. The
 * element at `directory[0]` contains the number of bytes held in `segments[0]`; the
 * element at `directory[1]` contains the number of bytes held in `segments[0] +
 * segments[1]`, and so on. The element at `directory[segments.length - 1]` contains the total
 * size of this byte string. The first half of the directory is always monotonically increasing.
 *
 * The second half of the directory is the offset in `segments` of the first content byte.
 * Bytes preceding this offset are unused, as are bytes beyond the segment's effective size.
 *
 * Suppose we have a byte string, `[A, B, C, D, E, F, G, H, I, J, K, L, M]` that is stored
 * across three byte arrays: `[x, x, x, x, A, B, C, D, E, x, x, x]`, `[x, F, G]`, and `[H, I, J, K,
 * L, M, x, x, x, x, x, x]`. The three byte arrays would be stored in `segments` in order. Since the
 * arrays contribute 5, 2, and 6 elements respectively, the directory starts with `[5, 7, 13` to
 * hold the cumulative total at each position. Since the offsets into the arrays are 4, 1, and 0
 * respectively, the directory ends with `4, 1, 0]`. Concatenating these two halves, the complete
 * directory is `[5, 7, 13, 4, 1, 0]`.
 *
 * This structure is chosen so that the segment holding a particular offset can be found by
 * binary search. We use one array rather than two for the directory as a micro-optimization.
 */
internal class SegmentedByteString private constructor(
  @Transient val segments: Array<ByteArray>,
  @Transient val directory: IntArray
) : ByteString(EMPTY.data) {

  companion object {
    fun of(buffer: Buffer, byteCount: Int): ByteString {
      checkOffsetAndCount(buffer.size, 0, byteCount.toLong())

      // Walk through the buffer to count how many segments we'll need.
      var offset = 0
      var segmentCount = 0
      var s = buffer.head
      while (offset < byteCount) {
        if (s!!.limit == s.pos) {
          throw AssertionError("s.limit == s.pos") // Empty segment. This should not happen!
        }
        offset += s.limit - s.pos
        segmentCount++
        s = s.next
      }

      // Walk through the buffer again to assign segments and build the directory.
      val segments = arrayOfNulls<ByteArray?>(segmentCount)
      val directory = IntArray(segmentCount * 2)
      offset = 0
      segmentCount = 0
      s = buffer.head
      while (offset < byteCount) {
        segments[segmentCount] = s!!.data
        offset += s.limit - s.pos
        // Despite sharing more bytes, only report having up to byteCount.
        directory[segmentCount] = minOf(offset, byteCount)
        directory[segmentCount + segments.size] = s.pos
        s.shared = true
        segmentCount++
        s = s.next
      }
      return SegmentedByteString(segments as Array<ByteArray>, directory)
    }
  }

  override fun string(charset: Charset) = toByteString().string(charset)

  override fun base64() = toByteString().base64()

  override fun hex() = toByteString().hex()

  override fun toAsciiLowercase() = toByteString().toAsciiLowercase()

  override fun toAsciiUppercase() = toByteString().toAsciiUppercase()

  override fun digest(algorithm: String): ByteString {
    val digest = MessageDigest.getInstance(algorithm)
    forEachSegment { data, offset, byteCount ->
      digest.update(data, offset, byteCount)
    }
    return ByteString(digest.digest())
  }

  override fun hmac(algorithm: String, key: ByteString): ByteString {
    try {
      val mac = Mac.getInstance(algorithm)
      mac.init(SecretKeySpec(key.toByteArray(), algorithm))
      forEachSegment { data, offset, byteCount ->
        mac.update(data, offset, byteCount)
      }
      return ByteString(mac.doFinal())
    } catch (e: InvalidKeyException) {
      throw IllegalArgumentException(e)
    }
  }

  override fun base64Url() = toByteString().base64Url()

  override fun substring(beginIndex: Int, endIndex: Int): ByteString {
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

  override fun internalGet(pos: Int): Byte {
    checkOffsetAndCount(directory[segments.size - 1].toLong(), pos.toLong(), 1)
    val segment = segment(pos)
    val segmentOffset = if (segment == 0) 0 else directory[segment - 1]
    val segmentPos = directory[segment + segments.size]
    return segments[segment][pos - segmentOffset + segmentPos]
  }

  /** Returns the index of the segment that contains the byte at `pos`.  */
  private fun segment(pos: Int): Int {
    // Search for (pos + 1) instead of (pos) because the directory holds sizes, not indexes.
    val i = Arrays.binarySearch(directory, 0, segments.size, pos + 1)
    return if (i >= 0) i else i.inv() // If i is negative, bitflip to get the insert position.
  }

  override fun getSize() = directory[segments.size - 1]

  override fun toByteArray(): ByteArray {
    val result = ByteArray(size)
    var resultPos = 0
    forEachSegment { data, offset, byteCount ->
      arraycopy(data, offset, result, resultPos, byteCount)
      resultPos += byteCount
    }
    return result
  }

  override fun asByteBuffer() = ByteBuffer.wrap(toByteArray()).asReadOnlyBuffer()

  @Throws(IOException::class)
  override fun write(out: OutputStream) {
    forEachSegment { data, offset, byteCount ->
      out.write(data, offset, byteCount)
    }
  }

  override fun write(buffer: Buffer) {
    forEachSegment { data, offset, byteCount ->
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

  override fun rangeEquals(
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

  override fun rangeEquals(
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

  override fun indexOf(other: ByteArray, fromIndex: Int) = toByteString().indexOf(other, fromIndex)

  override fun lastIndexOf(other: ByteArray, fromIndex: Int) = toByteString().lastIndexOf(other,
      fromIndex)

  /** Returns a copy as a non-segmented byte string.  */
  private fun toByteString() = ByteString(toByteArray())

  override fun internalArray() = toByteArray()

  /** Processes all segments, invoking `action` with the ByteArray and range of valid data. */
  private inline fun forEachSegment(
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
  private inline fun forEachSegment(
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

  override fun equals(other: Any?): Boolean {
    return when {
      other === this -> true
      other is ByteString -> other.size == size && rangeEquals(0, other, 0, size)
      else -> false
    }
  }

  override fun hashCode(): Int {
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

  override fun toString() = toByteString().toString()

  @Suppress("unused", "PLATFORM_CLASS_MAPPED_TO_KOTLIN") // For Java Serialization.
  private fun writeReplace(): Object = toByteString() as Object
}
