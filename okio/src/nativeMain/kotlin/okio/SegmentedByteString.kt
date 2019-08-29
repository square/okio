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

import okio.internal.commonEquals
import okio.internal.commonGetSize
import okio.internal.commonHashCode
import okio.internal.commonInternalGet
import okio.internal.commonRangeEquals
import okio.internal.commonSubstring
import okio.internal.commonToByteArray
import okio.internal.commonWrite

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
internal actual class SegmentedByteString internal actual constructor(
  internal actual val segments: Array<ByteArray>,
  internal actual val directory: IntArray
) : ByteString(EMPTY.data) {

  override fun base64() = toByteString().base64()

  override fun hex() = toByteString().hex()

  override fun toAsciiLowercase() = toByteString().toAsciiLowercase()

  override fun toAsciiUppercase() = toByteString().toAsciiUppercase()

  override fun base64Url() = toByteString().base64Url()

  override fun substring(beginIndex: Int, endIndex: Int): ByteString =
    commonSubstring(beginIndex, endIndex)

  override fun internalGet(pos: Int): Byte = commonInternalGet(pos)

  override fun getSize() = commonGetSize()

  override fun toByteArray(): ByteArray = commonToByteArray()

  override fun write(buffer: Buffer, offset: Int, byteCount: Int): Unit =
    commonWrite(buffer, offset, byteCount)

  override fun rangeEquals(
    offset: Int,
    other: ByteString,
    otherOffset: Int,
    byteCount: Int
  ): Boolean = commonRangeEquals(offset, other, otherOffset, byteCount)

  override fun rangeEquals(
    offset: Int,
    other: ByteArray,
    otherOffset: Int,
    byteCount: Int
  ): Boolean = commonRangeEquals(offset, other, otherOffset, byteCount)

  override fun indexOf(other: ByteArray, fromIndex: Int) = toByteString().indexOf(other, fromIndex)

  override fun lastIndexOf(other: ByteArray, fromIndex: Int) = toByteString().lastIndexOf(
    other,
    fromIndex
  )

  /** Returns a copy as a non-segmented byte string.  */
  private fun toByteString() = ByteString(toByteArray())

  override fun internalArray() = toByteArray()

  override fun equals(other: Any?): Boolean = commonEquals(other)

  override fun hashCode(): Int = commonHashCode()

  override fun toString() = toByteString().toString()
}
