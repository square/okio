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

import okio.Util.arrayRangeEquals
import okio.Util.checkOffsetAndCount
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.Arrays

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
internal class SegmentedByteString(buffer: Buffer, byteCount: Int) : ByteString(EMPTY.data) {
  @Transient val segments: Array<ByteArray>
  @Transient val directory: IntArray

  init {
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
    this.directory = IntArray(segmentCount * 2)
    offset = 0
    segmentCount = 0
    s = buffer.head
    while (offset < byteCount) {
      segments[segmentCount] = s!!.data
      offset += s.limit - s.pos
      if (offset > byteCount) {
        offset = byteCount // Despite sharing more bytes, only report having up to byteCount.
      }
      directory[segmentCount] = offset
      directory[segmentCount + segments.size] = s.pos
      s.shared = true
      segmentCount++
      s = s.next
    }
    this.segments = segments as Array<ByteArray>
  }

  override fun utf8() = toByteString().utf8()

  override fun string(charset: Charset) = toByteString().string(charset)

  override fun base64() = toByteString().base64()

  override fun hex() = toByteString().hex()

  override fun toAsciiLowercase() = toByteString().toAsciiLowercase()

  override fun toAsciiUppercase() = toByteString().toAsciiUppercase()

  override fun md5() = toByteString().md5()

  override fun sha1() = toByteString().sha1()

  override fun sha256() = toByteString().sha256()

  override fun sha512() = toByteString().sha512()

  override fun hmacSha1(key: ByteString) = toByteString().hmacSha1(key)

  override fun hmacSha256(key: ByteString) = toByteString().hmacSha256(key)

  override fun hmacSha512(key: ByteString) = toByteString().hmacSha512(key)

  override fun base64Url() = toByteString().base64Url()

  override fun substring(beginIndex: Int) = toByteString().substring(beginIndex)

  override fun substring(beginIndex: Int, endIndex: Int) = toByteString().substring(beginIndex,
      endIndex)

  override fun getByte(pos: Int): Byte {
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

  override fun size() = directory[segments.size - 1]

  override fun toByteArray(): ByteArray {
    val result = ByteArray(directory[segments.size - 1])
    var segmentOffset = 0
    var s = 0
    val segmentCount = segments.size
    while (s < segmentCount) {
      val segmentPos = directory[segmentCount + s]
      val nextSegmentOffset = directory[s]
      arraycopy(segments[s], segmentPos, result, segmentOffset,
          nextSegmentOffset - segmentOffset)
      segmentOffset = nextSegmentOffset
      s++
    }
    return result
  }

  override fun asByteBuffer() = ByteBuffer.wrap(toByteArray()).asReadOnlyBuffer()

  @Throws(IOException::class)
  override fun write(out: OutputStream) {
    var segmentOffset = 0
    var s = 0
    val segmentCount = segments.size
    while (s < segmentCount) {
      val segmentPos = directory[segmentCount + s]
      val nextSegmentOffset = directory[s]
      out.write(segments[s], segmentPos, nextSegmentOffset - segmentOffset)
      segmentOffset = nextSegmentOffset
      s++
    }
  }

  override fun write(buffer: Buffer) {
    var segmentOffset = 0
    var s = 0
    val segmentCount = segments.size
    while (s < segmentCount) {
      val segmentPos = directory[segmentCount + s]
      val nextSegmentOffset = directory[s]
      val segment = Segment(segments[s], segmentPos,
          segmentPos + nextSegmentOffset - segmentOffset, true, false)
      if (buffer.head == null) {
        segment.prev = segment
        segment.next = segment.prev
        buffer.head = segment.next
      } else {
        buffer.head!!.prev.push(segment)
      }
      segmentOffset = nextSegmentOffset
      s++
    }
    buffer.size += segmentOffset.toLong()
  }

  override fun rangeEquals(
    offset: Int, other: ByteString, otherOffset: Int, byteCount: Int
  ): Boolean {
    var offset = offset
    var otherOffset = otherOffset
    var byteCount = byteCount
    if (offset < 0 || offset > size() - byteCount) return false
    // Go segment-by-segment through this, passing arrays to other's rangeEquals().
    var s = segment(offset)
    while (byteCount > 0) {
      val segmentOffset = if (s == 0) 0 else directory[s - 1]
      val segmentSize = directory[s] - segmentOffset
      val stepSize = Math.min(byteCount, segmentOffset + segmentSize - offset)
      val segmentPos = directory[segments.size + s]
      val arrayOffset = offset - segmentOffset + segmentPos
      if (!other.rangeEquals(otherOffset, segments[s], arrayOffset, stepSize)) return false
      offset += stepSize
      otherOffset += stepSize
      byteCount -= stepSize
      s++
    }
    return true
  }

  override fun rangeEquals(
    offset: Int,
    other: ByteArray,
    otherOffset: Int,
    byteCount: Int
  ): Boolean {
    var offset = offset
    var otherOffset = otherOffset
    var byteCount = byteCount
    if (offset < 0 || offset > size() - byteCount
        || otherOffset < 0 || otherOffset > other.size - byteCount) {
      return false
    }
    // Go segment-by-segment through this, comparing ranges of arrays.
    var s = segment(offset)
    while (byteCount > 0) {
      val segmentOffset = if (s == 0) 0 else directory[s - 1]
      val segmentSize = directory[s] - segmentOffset
      val stepSize = Math.min(byteCount, segmentOffset + segmentSize - offset)
      val segmentPos = directory[segments.size + s]
      val arrayOffset = offset - segmentOffset + segmentPos
      if (!arrayRangeEquals(segments[s], arrayOffset, other, otherOffset, stepSize)) return false
      offset += stepSize
      otherOffset += stepSize
      byteCount -= stepSize
      s++
    }
    return true
  }

  override fun indexOf(other: ByteArray, fromIndex: Int) = toByteString().indexOf(other, fromIndex)

  override fun lastIndexOf(other: ByteArray, fromIndex: Int) = toByteString().lastIndexOf(other,
      fromIndex)

  /** Returns a copy as a non-segmented byte string.  */
  private fun toByteString() = ByteString(toByteArray())

  override fun internalArray() = toByteArray()

  override fun equals(other: Any?): Boolean {
    return when {
      other === this -> true
      other is ByteString -> other.size() == size() && rangeEquals(0, other, 0, size())
      else -> false
    }
  }

  override fun hashCode(): Int {
    var result = hashCode
    if (result != 0) return result

    // Equivalent to Arrays.hashCode(toByteArray()).
    result = 1
    var segmentOffset = 0
    var s = 0
    val segmentCount = segments.size
    while (s < segmentCount) {
      val segment = segments[s]
      val segmentPos = directory[segmentCount + s]
      val nextSegmentOffset = directory[s]
      val segmentSize = nextSegmentOffset - segmentOffset
      var i = segmentPos
      val limit = segmentPos + segmentSize
      while (i < limit) {
        result = 31 * result + segment[i]
        i++
      }
      segmentOffset = nextSegmentOffset
      s++
    }
    hashCode = result
    return result
  }

  override fun toString() = toByteString().toString()

  @Suppress("unused", "PLATFORM_CLASS_MAPPED_TO_KOTLIN") // For Java Serialization.
  private fun writeReplace(): Object = toByteString() as Object
}
