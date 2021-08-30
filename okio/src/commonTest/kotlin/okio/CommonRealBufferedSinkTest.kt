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
import kotlin.test.assertFailsWith
import kotlin.test.fail

/**
 * Tests solely for the behavior of RealBufferedSink's implementation. For generic
 * BufferedSink behavior use BufferedSinkTest.
 */
class CommonRealBufferedSinkTest {
  @Test fun bufferedSinkEmitsTailWhenItIsComplete() {
    val sink = Buffer()
    val bufferedSink = (sink as Sink).buffer()
    bufferedSink.writeUtf8("a".repeat(Segment.SIZE - 1))
    assertEquals(0, sink.size)
    bufferedSink.writeByte(0)
    assertEquals(Segment.SIZE.toLong(), sink.size)
    assertEquals(0, bufferedSink.buffer.size)
  }

  @Test fun bufferedSinkEmitMultipleSegments() {
    val sink = Buffer()
    val bufferedSink = (sink as Sink).buffer()
    bufferedSink.writeUtf8("a".repeat(Segment.SIZE * 4 - 1))
    assertEquals(Segment.SIZE.toLong() * 3L, sink.size)
    assertEquals(Segment.SIZE.toLong() - 1L, bufferedSink.buffer.size)
  }

  @Test fun bufferedSinkFlush() {
    val sink = Buffer()
    val bufferedSink = (sink as Sink).buffer()
    bufferedSink.writeByte('a'.code)
    assertEquals(0, sink.size)
    bufferedSink.flush()
    assertEquals(0, bufferedSink.buffer.size)
    assertEquals(1, sink.size)
  }

  @Test fun bytesEmittedToSinkWithFlush() {
    val sink = Buffer()
    val bufferedSink = (sink as Sink).buffer()
    bufferedSink.writeUtf8("abc")
    bufferedSink.flush()
    assertEquals(3, sink.size)
  }

  @Test fun bytesNotEmittedToSinkWithoutFlush() {
    val sink = Buffer()
    val bufferedSink = (sink as Sink).buffer()
    bufferedSink.writeUtf8("abc")
    assertEquals(0, sink.size)
  }

  @Test fun bytesEmittedToSinkWithEmit() {
    val sink = Buffer()
    val bufferedSink = (sink as Sink).buffer()
    bufferedSink.writeUtf8("abc")
    bufferedSink.emit()
    assertEquals(3, sink.size)
  }

  @Test fun completeSegmentsEmitted() {
    val sink = Buffer()
    val bufferedSink = (sink as Sink).buffer()
    bufferedSink.writeUtf8("a".repeat(Segment.SIZE * 3))
    assertEquals(Segment.SIZE.toLong() * 3L, sink.size)
  }

  @Test fun incompleteSegmentsNotEmitted() {
    val sink = Buffer()
    val bufferedSink = (sink as Sink).buffer()
    bufferedSink.writeUtf8("a".repeat(Segment.SIZE * 3 - 1))
    assertEquals(Segment.SIZE.toLong() * 2L, sink.size)
  }

  @Test fun closeWithExceptionWhenWriting() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, IOException())
    val bufferedSink = mockSink.buffer()
    bufferedSink.writeByte('a'.code)
    assertFailsWith<IOException> {
      bufferedSink.close()
    }

    mockSink.assertLog("write([text=a], 1)", "close()")
  }

  @Test fun closeWithExceptionWhenClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(1, IOException())
    val bufferedSink = mockSink.buffer()
    bufferedSink.writeByte('a'.code)
    assertFailsWith<IOException> {
      bufferedSink.close()
    }

    mockSink.assertLog("write([text=a], 1)", "close()")
  }

  @Test fun closeWithExceptionWhenWritingAndClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, IOException("first"))
    mockSink.scheduleThrow(1, IOException("second"))
    val bufferedSink = mockSink.buffer()
    bufferedSink.writeByte('a'.code)
    try {
      bufferedSink.close()
      fail()
    } catch (expected: IOException) {
      assertEquals("first", expected.message)
    }

    mockSink.assertLog("write([text=a], 1)", "close()")
  }

  @Test fun operationsAfterClose() {
    val mockSink = MockSink()
    val bufferedSink = mockSink.buffer()
    bufferedSink.writeByte('a'.code)
    bufferedSink.close()

    // Test a sample set of methods.
    assertFailsWith<IllegalStateException> {
      bufferedSink.writeByte('a'.code)
    }

    assertFailsWith<IllegalStateException> {
      bufferedSink.write(ByteArray(10))
    }

    assertFailsWith<IllegalStateException> {
      bufferedSink.emitCompleteSegments()
    }

    assertFailsWith<IllegalStateException> {
      bufferedSink.emit()
    }

    assertFailsWith<IllegalStateException> {
      bufferedSink.flush()
    }
  }

  @Test fun writeAll() {
    val mockSink = MockSink()
    val bufferedSink = mockSink.buffer()

    bufferedSink.buffer.writeUtf8("abc")
    assertEquals(3, bufferedSink.writeAll(Buffer().writeUtf8("def")))

    assertEquals(6, bufferedSink.buffer.size)
    assertEquals("abcdef", bufferedSink.buffer.readUtf8(6))
    mockSink.assertLog() // No writes.
  }

  @Test fun writeAllExhausted() {
    val mockSink = MockSink()
    val bufferedSink = mockSink.buffer()

    assertEquals(0, bufferedSink.writeAll(Buffer()))
    assertEquals(0, bufferedSink.buffer.size)
    mockSink.assertLog() // No writes.
  }

  @Test fun writeAllWritesOneSegmentAtATime() {
    val write1 = Buffer().writeUtf8("a".repeat(Segment.SIZE))
    val write2 = Buffer().writeUtf8("b".repeat(Segment.SIZE))
    val write3 = Buffer().writeUtf8("c".repeat(Segment.SIZE))

    val source = Buffer().writeUtf8(
      "${"a".repeat(Segment.SIZE)}${"b".repeat(Segment.SIZE)}${"c".repeat(Segment.SIZE)}"
    )

    val mockSink = MockSink()
    val bufferedSink = mockSink.buffer()
    assertEquals(Segment.SIZE.toLong() * 3L, bufferedSink.writeAll(source))

    mockSink.assertLog(
      "write($write1, ${write1.size})",
      "write($write2, ${write2.size})",
      "write($write3, ${write3.size})"
    )
  }
}
