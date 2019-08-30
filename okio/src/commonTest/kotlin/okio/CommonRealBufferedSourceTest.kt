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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Tests solely for the behavior of RealBufferedSource's implementation. For generic
 * BufferedSource behavior use BufferedSourceTest.
 */
class CommonRealBufferedSourceTest {
  @Test fun indexOfStopsReadingAtLimit() {
    val buffer = Buffer().writeUtf8("abcdef")
    val bufferedSource = (object : Source by buffer {
      override fun read(sink: Buffer, byteCount: Long): Long {
        return buffer.read(sink, minOf(1, byteCount))
      }
    }).buffer()

    assertEquals(6, buffer.size)
    assertEquals(-1, bufferedSource.indexOf('e'.toByte(), 0, 4))
    assertEquals(2, buffer.size)
  }

  @Test fun requireTracksBufferFirst() {
    val source = Buffer()
    source.writeUtf8("bb")

    val bufferedSource = (source as Source).buffer()
    bufferedSource.buffer.writeUtf8("aa")

    bufferedSource.require(2)
    assertEquals(2, bufferedSource.buffer.size)
    assertEquals(2, source.size)
  }

  @Test fun requireIncludesBufferBytes() {
    val source = Buffer()
    source.writeUtf8("b")

    val bufferedSource = (source as Source).buffer()
    bufferedSource.buffer.writeUtf8("a")

    bufferedSource.require(2)
    assertEquals("ab", bufferedSource.buffer.readUtf8(2))
  }

  @Test fun requireInsufficientData() {
    val source = Buffer()
    source.writeUtf8("a")

    val bufferedSource = (source as Source).buffer()

    try {
      bufferedSource.require(2)
      fail()
    } catch (expected: EOFException) {
    }
  }

  @Test fun requireReadsOneSegmentAtATime() {
    val source = Buffer()
    source.writeUtf8("a".repeat(Segment.SIZE))
    source.writeUtf8("b".repeat(Segment.SIZE))

    val bufferedSource = (source as Source).buffer()

    bufferedSource.require(2)
    assertEquals(Segment.SIZE.toLong(), source.size)
    assertEquals(Segment.SIZE.toLong(), bufferedSource.buffer.size)
  }

  @Test fun skipReadsOneSegmentAtATime() {
    val source = Buffer()
    source.writeUtf8("a".repeat(Segment.SIZE))
    source.writeUtf8("b".repeat(Segment.SIZE))
    val bufferedSource = (source as Source).buffer()
    bufferedSource.skip(2)
    assertEquals(Segment.SIZE.toLong(), source.size)
    assertEquals(Segment.SIZE.toLong() - 2L, bufferedSource.buffer.size)
  }

  @Test fun skipTracksBufferFirst() {
    val source = Buffer()
    source.writeUtf8("bb")

    val bufferedSource = (source as Source).buffer()
    bufferedSource.buffer.writeUtf8("aa")

    bufferedSource.skip(2)
    assertEquals(0, bufferedSource.buffer.size)
    assertEquals(2, source.size)
  }

  @Test fun operationsAfterClose() {
    val source = Buffer()
    val bufferedSource = (source as Source).buffer()
    bufferedSource.close()

    // Test a sample set of methods.
    try {
      bufferedSource.indexOf(1.toByte())
      fail()
    } catch (expected: IllegalStateException) {
    }

    try {
      bufferedSource.skip(1)
      fail()
    } catch (expected: IllegalStateException) {
    }

    try {
      bufferedSource.readByte()
      fail()
    } catch (expected: IllegalStateException) {
    }

    try {
      bufferedSource.readByteString(10)
      fail()
    } catch (expected: IllegalStateException) {
    }
  }

  /**
   * We don't want readAll to buffer an unbounded amount of data. Instead it
   * should buffer a segment, write it, and repeat.
   */
  @Test fun readAllReadsOneSegmentAtATime() {
    val write1 = Buffer().writeUtf8("a".repeat(Segment.SIZE))
    val write2 = Buffer().writeUtf8("b".repeat(Segment.SIZE))
    val write3 = Buffer().writeUtf8("c".repeat(Segment.SIZE))

    val source = Buffer().writeUtf8(
      "${"a".repeat(Segment.SIZE)}${"b".repeat(Segment.SIZE)}${"c".repeat(Segment.SIZE)}"
    )

    val mockSink = MockSink()
    val bufferedSource = (source as Source).buffer()
    assertEquals(Segment.SIZE.toLong() * 3L, bufferedSource.readAll(mockSink))
    mockSink.assertLog(
      "write($write1, ${write1.size})",
      "write($write2, ${write2.size})",
      "write($write3, ${write3.size})"
    )
  }
}
