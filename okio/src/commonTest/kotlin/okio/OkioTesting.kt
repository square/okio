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
package okio

import kotlin.random.Random
import kotlin.test.assertTrue

fun assertNoEmptySegments(buffer: Buffer) {
  assertTrue(segmentSizes(buffer).all { it != 0 }, "Expected all segments to be non-empty")
}

fun segmentSizes(buffer: Buffer): List<Int> {
  var segment = buffer.head ?: return emptyList()

  val sizes = mutableListOf(segment.limit - segment.pos)
  segment = segment.next!!
  while (segment !== buffer.head) {
    sizes.add(segment.limit - segment.pos)
    segment = segment.next!!
  }
  return sizes
}

fun bufferWithRandomSegmentLayout(dice: Random, data: ByteArray): Buffer {
  val result = Buffer()

  // Writing to result directly will yield packed segments. Instead, write to
  // other buffers, then write those buffers to result.
  var pos = 0
  var byteCount: Int
  while (pos < data.size) {
    byteCount = Segment.SIZE / 2 + dice.nextInt(Segment.SIZE / 2)
    if (byteCount > data.size - pos) byteCount = data.size - pos
    val offset = dice.nextInt(Segment.SIZE - byteCount)

    val segment = Buffer()
    segment.write(ByteArray(offset))
    segment.write(data, pos, byteCount)
    segment.skip(offset.toLong())

    result.write(segment, byteCount.toLong())
    pos += byteCount
  }

  return result
}

fun bufferWithSegments(vararg segments: String): Buffer {
  val result = Buffer()
  for (s in segments) {
    val offsetInSegment = if (s.length < Segment.SIZE) (Segment.SIZE - s.length) / 2 else 0
    val buffer = Buffer()
    buffer.writeUtf8('_'.repeat(offsetInSegment))
    buffer.writeUtf8(s)
    buffer.skip(offsetInSegment.toLong())
    result.write(buffer.copyTo(Buffer()), buffer.size)
  }
  return result
}

fun makeSegments(source: ByteString): ByteString {
  val buffer = Buffer()
  for (i in 0 until source.size) {
    val segment = buffer.writableSegment(Segment.SIZE)
    segment.data[segment.pos] = source[i]
    segment.limit++
    buffer.size++
  }
  return buffer.snapshot()
}

/**
 * Returns a string with all '\' slashes replaced with '/' slashes. This is useful for test
 * assertions that intend to ignore slashes.
 */
fun Path.withUnixSlashes(): String {
  return toString().replace('\\', '/')
}

expect fun assertRelativeTo(
  a: Path,
  b: Path,
  bRelativeToA: Path,
  sameAsNio: Boolean = true,
)

expect fun assertRelativeToFails(
  a: Path,
  b: Path,
  sameAsNio: Boolean = true,
): IllegalArgumentException

expect fun <T> withUtc(block: () -> T): T
