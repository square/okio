/*
 * Copyright (C) 2014 Square, Inc.
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.ByteString.Companion.decodeHex

/**
 * Tests solely for the behavior of Buffer's implementation. For generic BufferedSink or
 * BufferedSource behavior use BufferedSinkTest or BufferedSourceTest, respectively.
 */
class CommonBufferTest {
  @Test fun readAndWriteUtf8() {
    val buffer = Buffer()
    buffer.writeUtf8("ab")
    assertEquals(2, buffer.size)
    buffer.writeUtf8("cdef")
    assertEquals(6, buffer.size)
    assertEquals("abcd", buffer.readUtf8(4))
    assertEquals(2, buffer.size)
    assertEquals("ef", buffer.readUtf8(2))
    assertEquals(0, buffer.size)
    assertFailsWith<EOFException> {
      buffer.readUtf8(1)
    }
  }

  /** Buffer's toString is the same as ByteString's.  */
  @Test fun bufferToString() {
    assertEquals("[size=0]", Buffer().toString())
    assertEquals(
      "[text=a\\r\\nb\\nc\\rd\\\\e]",
      Buffer().writeUtf8("a\r\nb\nc\rd\\e").toString(),
    )
    assertEquals(
      "[text=Tyrannosaur]",
      Buffer().writeUtf8("Tyrannosaur").toString(),
    )
    assertEquals(
      "[text=təˈranəˌsôr]",
      Buffer()
        .write("74c999cb8872616ec999cb8c73c3b472".decodeHex())
        .toString(),
    )
    assertEquals(
      "[hex=0000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "0000000000000000000000000000000000000000000000000000]",
      Buffer().write(ByteArray(64)).toString(),
    )
  }

  @Test fun multipleSegmentBuffers() {
    val buffer = Buffer()
    buffer.writeUtf8('a'.repeat(1000))
    buffer.writeUtf8('b'.repeat(2500))
    buffer.writeUtf8('c'.repeat(5000))
    buffer.writeUtf8('d'.repeat(10000))
    buffer.writeUtf8('e'.repeat(25000))
    buffer.writeUtf8('f'.repeat(50000))

    assertEquals('a'.repeat(999), buffer.readUtf8(999)) // a...a
    assertEquals("a" + 'b'.repeat(2500) + "c", buffer.readUtf8(2502)) // ab...bc
    assertEquals('c'.repeat(4998), buffer.readUtf8(4998)) // c...c
    assertEquals("c" + 'd'.repeat(10000) + "e", buffer.readUtf8(10002)) // cd...de
    assertEquals('e'.repeat(24998), buffer.readUtf8(24998)) // e...e
    assertEquals("e" + 'f'.repeat(50000), buffer.readUtf8(50001)) // ef...f
    assertEquals(0, buffer.size)
  }

  @Test fun fillAndDrainPool() {
    val buffer = Buffer()

    // Take 2 * MAX_SIZE segments. This will drain the pool, even if other tests filled it.
    buffer.write(ByteArray(SegmentPool.MAX_SIZE))
    buffer.write(ByteArray(SegmentPool.MAX_SIZE))
    assertEquals(0, SegmentPool.byteCount)

    // Recycle MAX_SIZE segments. They're all in the pool.
    buffer.skip(SegmentPool.MAX_SIZE.toLong())
    assertEquals(SegmentPool.MAX_SIZE, SegmentPool.byteCount)

    // Recycle MAX_SIZE more segments. The pool is full so they get garbage collected.
    buffer.skip(SegmentPool.MAX_SIZE.toLong())
    assertEquals(SegmentPool.MAX_SIZE, SegmentPool.byteCount)

    // Take MAX_SIZE segments to drain the pool.
    buffer.write(ByteArray(SegmentPool.MAX_SIZE))
    assertEquals(0, SegmentPool.byteCount)

    // Take MAX_SIZE more segments. The pool is drained so these will need to be allocated.
    buffer.write(ByteArray(SegmentPool.MAX_SIZE))
    assertEquals(0, SegmentPool.byteCount)
  }

  @Test fun moveBytesBetweenBuffersShareSegment() {
    val size = Segment.SIZE / 2 - 1
    val segmentSizes = moveBytesBetweenBuffers('a'.repeat(size), 'b'.repeat(size))
    assertEquals(listOf(size * 2), segmentSizes)
  }

  @Test fun moveBytesBetweenBuffersReassignSegment() {
    val size = Segment.SIZE / 2 + 1
    val segmentSizes = moveBytesBetweenBuffers('a'.repeat(size), 'b'.repeat(size))
    assertEquals(listOf(size, size), segmentSizes)
  }

  @Test fun moveBytesBetweenBuffersMultipleSegments() {
    val size = 3 * Segment.SIZE + 1
    val segmentSizes = moveBytesBetweenBuffers('a'.repeat(size), 'b'.repeat(size))
    assertEquals(
      listOf(
        Segment.SIZE,
        Segment.SIZE,
        Segment.SIZE,
        1,
        Segment.SIZE,
        Segment.SIZE,
        Segment.SIZE,
        1,
      ),
      segmentSizes,
    )
  }

  private fun moveBytesBetweenBuffers(vararg contents: String): List<Int> {
    val expected = StringBuilder()
    val buffer = Buffer()
    for (s in contents) {
      val source = Buffer()
      source.writeUtf8(s)
      buffer.writeAll(source)
      expected.append(s)
    }
    val segmentSizes = segmentSizes(buffer)
    assertEquals(expected.toString(), buffer.readUtf8(expected.length.toLong()))
    return segmentSizes
  }

  /** The big part of source's first segment is being moved.  */
  @Test fun writeSplitSourceBufferLeft() {
    val writeSize = Segment.SIZE / 2 + 1

    val sink = Buffer()
    sink.writeUtf8('b'.repeat(Segment.SIZE - 10))

    val source = Buffer()
    source.writeUtf8('a'.repeat(Segment.SIZE * 2))
    sink.write(source, writeSize.toLong())

    assertEquals(listOf(Segment.SIZE - 10, writeSize), segmentSizes(sink))
    assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), segmentSizes(source))
  }

  /** The big part of source's first segment is staying put.  */
  @Test fun writeSplitSourceBufferRight() {
    val writeSize = Segment.SIZE / 2 - 1

    val sink = Buffer()
    sink.writeUtf8('b'.repeat(Segment.SIZE - 10))

    val source = Buffer()
    source.writeUtf8('a'.repeat(Segment.SIZE * 2))
    sink.write(source, writeSize.toLong())

    assertEquals(listOf(Segment.SIZE - 10, writeSize), segmentSizes(sink))
    assertEquals(listOf(Segment.SIZE - writeSize, Segment.SIZE), segmentSizes(source))
  }

  @Test fun writePrefixDoesntSplit() {
    val sink = Buffer()
    sink.writeUtf8('b'.repeat(10))

    val source = Buffer()
    source.writeUtf8('a'.repeat(Segment.SIZE * 2))
    sink.write(source, 20)

    assertEquals(listOf(30), segmentSizes(sink))
    assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), segmentSizes(source))
    assertEquals(30, sink.size)
    assertEquals((Segment.SIZE * 2 - 20).toLong(), source.size)
  }

  @Test fun writePrefixDoesntSplitButRequiresCompact() {
    val sink = Buffer()
    sink.writeUtf8('b'.repeat(Segment.SIZE - 10)) // limit = size - 10
    sink.readUtf8((Segment.SIZE - 20).toLong()) // pos = size = 20

    val source = Buffer()
    source.writeUtf8('a'.repeat(Segment.SIZE * 2))
    sink.write(source, 20)

    assertEquals(listOf(30), segmentSizes(sink))
    assertEquals(listOf(Segment.SIZE - 20, Segment.SIZE), segmentSizes(source))
    assertEquals(30, sink.size)
    assertEquals((Segment.SIZE * 2 - 20).toLong(), source.size)
  }

  @Test fun moveAllRequestedBytesWithRead() {
    val sink = Buffer()
    sink.writeUtf8('a'.repeat(10))

    val source = Buffer()
    source.writeUtf8('b'.repeat(15))

    assertEquals(10, source.read(sink, 10))
    assertEquals(20, sink.size)
    assertEquals(5, source.size)
    assertEquals('a'.repeat(10) + 'b'.repeat(10), sink.readUtf8(20))
  }

  @Test fun moveFewerThanRequestedBytesWithRead() {
    val sink = Buffer()
    sink.writeUtf8('a'.repeat(10))

    val source = Buffer()
    source.writeUtf8('b'.repeat(20))

    assertEquals(20, source.read(sink, 25))
    assertEquals(30, sink.size)
    assertEquals(0, source.size)
    assertEquals('a'.repeat(10) + 'b'.repeat(20), sink.readUtf8(30))
  }

  @Test fun indexOfWithOffset() {
    val buffer = Buffer()
    val halfSegment = Segment.SIZE / 2
    buffer.writeUtf8('a'.repeat(halfSegment))
    buffer.writeUtf8('b'.repeat(halfSegment))
    buffer.writeUtf8('c'.repeat(halfSegment))
    buffer.writeUtf8('d'.repeat(halfSegment))
    assertEquals(0, buffer.indexOf('a'.code.toByte(), 0))
    assertEquals((halfSegment - 1).toLong(), buffer.indexOf('a'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals(halfSegment.toLong(), buffer.indexOf('b'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 2).toLong(), buffer.indexOf('c'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment - 1).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 2).toLong()))
    assertEquals((halfSegment * 3).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 3).toLong()))
    assertEquals((halfSegment * 4 - 1).toLong(), buffer.indexOf('d'.code.toByte(), (halfSegment * 4 - 1).toLong()))
  }

  @Test fun byteAt() {
    val buffer = Buffer()
    buffer.writeUtf8("a")
    buffer.writeUtf8('b'.repeat(Segment.SIZE))
    buffer.writeUtf8("c")
    assertEquals('a'.code.toLong(), buffer[0].toLong())
    assertEquals('a'.code.toLong(), buffer[0].toLong()) // getByte doesn't mutate!
    assertEquals('c'.code.toLong(), buffer[buffer.size - 1].toLong())
    assertEquals('b'.code.toLong(), buffer[buffer.size - 2].toLong())
    assertEquals('b'.code.toLong(), buffer[buffer.size - 3].toLong())
  }

  @Test fun getByteOfEmptyBuffer() {
    val buffer = Buffer()
    assertFailsWith<IndexOutOfBoundsException> {
      buffer[0]
    }
  }

  @Test
  fun writePrefixToEmptyBuffer() {
    val sink = Buffer()
    val source = Buffer()
    source.writeUtf8("abcd")
    sink.write(source, 2)
    assertEquals("ab", sink.readUtf8(2))
  }

  @Suppress("ReplaceAssertBooleanWithAssertEquality")
  @Test
  fun equalsAndHashCodeEmpty() {
    val a = Buffer()
    val b = Buffer()
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())
  }

  @Suppress("ReplaceAssertBooleanWithAssertEquality")
  @Test
  fun equalsAndHashCode() {
    val a = Buffer().writeUtf8("dog")
    val b = Buffer().writeUtf8("hotdog")
    assertFalse(a == b)
    assertFalse(a.hashCode() == b.hashCode())

    b.readUtf8(3) // Leaves b containing 'dog'.
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())
  }

  @Suppress("ReplaceAssertBooleanWithAssertEquality")
  @Test
  fun equalsAndHashCodeSpanningSegments() {
    val data = ByteArray(1024 * 1024)
    val dice = Random(0)
    dice.nextBytes(data)

    val a = bufferWithRandomSegmentLayout(dice, data)
    val b = bufferWithRandomSegmentLayout(dice, data)
    assertTrue(a == b)
    assertTrue(a.hashCode() == b.hashCode())

    data[data.size / 2]++ // Change a single byte.
    val c = bufferWithRandomSegmentLayout(dice, data)
    assertFalse(a == c)
    assertFalse(a.hashCode() == c.hashCode())
  }

  /**
   * When writing data that's already buffered, there's no reason to page the
   * data by segment.
   */
  @Test fun readAllWritesAllSegmentsAtOnce() {
    val write1 = Buffer().writeUtf8(
      'a'.repeat(Segment.SIZE) +
        'b'.repeat(Segment.SIZE) +
        'c'.repeat(Segment.SIZE),
    )

    val source = Buffer().writeUtf8(
      'a'.repeat(Segment.SIZE) +
        'b'.repeat(Segment.SIZE) +
        'c'.repeat(Segment.SIZE),
    )

    val mockSink = MockSink()

    assertEquals((Segment.SIZE * 3).toLong(), source.readAll(mockSink))
    assertEquals(0, source.size)
    mockSink.assertLog("write($write1, ${write1.size})")
  }

  @Test fun writeAllMultipleSegments() {
    val source = Buffer().writeUtf8('a'.repeat(Segment.SIZE * 3))
    val sink = Buffer()

    assertEquals((Segment.SIZE * 3).toLong(), sink.writeAll(source))
    assertEquals(0, source.size)
    assertEquals('a'.repeat(Segment.SIZE * 3), sink.readUtf8())
  }

  @Test fun copyTo() {
    val source = Buffer()
    source.writeUtf8("party")

    val target = Buffer()
    source.copyTo(target, 1, 3)

    assertEquals("art", target.readUtf8())
    assertEquals("party", source.readUtf8())
  }

  @Test fun copyToOnSegmentBoundary() {
    val `as` = 'a'.repeat(Segment.SIZE)
    val bs = 'b'.repeat(Segment.SIZE)
    val cs = 'c'.repeat(Segment.SIZE)
    val ds = 'd'.repeat(Segment.SIZE)

    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)
    source.writeUtf8(cs)

    val target = Buffer()
    target.writeUtf8(ds)

    source.copyTo(target, `as`.length.toLong(), (bs.length + cs.length).toLong())
    assertEquals(ds + bs + cs, target.readUtf8())
  }

  @Test fun copyToOffSegmentBoundary() {
    val `as` = 'a'.repeat(Segment.SIZE - 1)
    val bs = 'b'.repeat(Segment.SIZE + 2)
    val cs = 'c'.repeat(Segment.SIZE - 4)
    val ds = 'd'.repeat(Segment.SIZE + 8)

    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)
    source.writeUtf8(cs)

    val target = Buffer()
    target.writeUtf8(ds)

    source.copyTo(target, `as`.length.toLong(), (bs.length + cs.length).toLong())
    assertEquals(ds + bs + cs, target.readUtf8())
  }

  @Test fun copyToSourceAndTargetCanBeTheSame() {
    val `as` = 'a'.repeat(Segment.SIZE)
    val bs = 'b'.repeat(Segment.SIZE)

    val source = Buffer()
    source.writeUtf8(`as`)
    source.writeUtf8(bs)

    source.copyTo(source, 0, source.size)
    assertEquals(`as` + bs + `as` + bs, source.readUtf8())
  }

  @Test fun copyToEmptySource() {
    val source = Buffer()
    val target = Buffer().writeUtf8("aaa")
    source.copyTo(target, 0L, 0L)
    assertEquals("", source.readUtf8())
    assertEquals("aaa", target.readUtf8())
  }

  @Test fun copyToEmptyTarget() {
    val source = Buffer().writeUtf8("aaa")
    val target = Buffer()
    source.copyTo(target, 0L, 3L)
    assertEquals("aaa", source.readUtf8())
    assertEquals("aaa", target.readUtf8())
  }

  @Test fun snapshotReportsAccurateSize() {
    val buf = Buffer().write(byteArrayOf(0, 1, 2, 3))
    assertEquals(1, buf.snapshot(1).size)
  }
}
