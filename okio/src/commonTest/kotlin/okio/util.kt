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
import kotlin.test.assertEquals

fun Char.repeat(count: Int): String {
  return toString().repeat(count)
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

fun assertArrayEquals(a: ByteArray, b: ByteArray) {
  assertEquals(a.contentToString(), b.contentToString())
}

fun randomBytes(length: Int): ByteString {
  val random = Random(0)
  val randomBytes = ByteArray(length)
  random.nextBytes(randomBytes)
  return ByteString.of(*randomBytes)
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
