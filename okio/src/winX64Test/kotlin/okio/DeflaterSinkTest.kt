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

package okio

import platform.zlib.Z_NO_COMPRESSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class DeflaterSinkTest {
  @Test fun deflateWithClose() {
    val data = Buffer()
    val original = "They're moving in herds. They do move in herds."
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink)
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test fun deflateWithSyncFlush() {
    val original = "Yes, yes, yes. That's why we're taking extreme precautions."
    val data = Buffer()
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink)
    deflaterSink.write(data, data.size)
    deflaterSink.flush()

    // TODO zlib does not consider the stream complete unless sink is closed.
    //  JVM inflater does not require this; why? Zlib writes an additional 6 bytes to the buffer
    //  when the sink is closed; what are those bytes and what do they mean?
    deflaterSink.close()

    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test fun deflateWellCompressed() {
    val original = 'a'.repeat(1024 * 1024)
    val data = Buffer()
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink)
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test fun deflatePoorlyCompressed() {
    val original = randomBytes(1024 * 1024)
    val data = Buffer()
    data.write(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink)
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readByteString())
  }

  @Test fun multipleSegmentsWithoutCompression() {
    val buffer = Buffer()
    val deflater = Deflater(Z_NO_COMPRESSION)
    val deflaterSink = DeflaterSink(buffer, deflater)
    val byteCount = Segment.SIZE * 4
    deflaterSink.write(Buffer().writeUtf8('a'.repeat(byteCount)), byteCount.toLong())
    deflaterSink.close()
    assertEquals('a'.repeat(byteCount), inflate(buffer).readUtf8(byteCount.toLong()))
  }

  @Test fun deflateIntoNonemptySink() {
    val original = "They're moving in herds. They do move in herds."

    // Exercise all possible offsets for the outgoing segment.
    for (i in 0 until Segment.SIZE) {
      val data = Buffer().writeUtf8(original)
      val sink = Buffer().writeUtf8('a'.repeat(i))

      val deflaterSink = DeflaterSink(sink)
      deflaterSink.write(data, data.size)
      deflaterSink.close()

      sink.skip(i.toLong())
      val inflated = inflate(sink)
      assertEquals(original, inflated.readUtf8())
    }
  }

  /**
   * This test deflates a single segment of without compression because that's
   * the easiest way to force close() to emit a large amount of data to the
   * underlying sink.
   */
  @Test fun closeWithExceptionWhenWritingAndClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, IOException("first"))
    mockSink.scheduleThrow(1, IOException("second"))
    val deflater = Deflater(Z_NO_COMPRESSION)
    val deflaterSink = DeflaterSink(mockSink, deflater)
    deflaterSink.write(Buffer().writeUtf8('a'.repeat(Segment.SIZE)), Segment.SIZE.toLong())
    try {
      deflaterSink.close()
      fail()
    } catch (expected: IOException) {
      assertEquals("first", expected.message)
    }

    mockSink.assertLogContains("close()")
  }

  /** Returns a new buffer containing the inflated contents of `deflated`.  */
  private fun inflate(deflated: Buffer): Buffer {
    val result = Buffer()
    val source = InflaterSource(deflated)
    source.readAll(result)
    return result
  }

  private fun Source.readAll(sink: Buffer) {
    while (read(sink, Int.MAX_VALUE.toLong()) != -1L) {
    }
  }
}
