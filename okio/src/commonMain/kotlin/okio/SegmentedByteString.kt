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
internal expect class SegmentedByteString internal constructor(
  segments: Array<ByteArray>,
  directory: IntArray
) : ByteString {

  internal val segments: Array<ByteArray>
  internal val directory: IntArray
}
