/*
 * Copyright (C) 2020 Square, Inc.
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

import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.NSInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

@OptIn(UnsafeNumber::class)
class AppleSourceTest {
  @Test fun nsInputStreamSource() {
    val nsis = NSInputStream(byteArrayOf(0x61).toNSData())
    val source = nsis.source()
    val buffer = Buffer()
    source.read(buffer, 1)
    assertEquals("a", buffer.readUtf8())
  }

  @Test fun sourceFromInputStream() {
    val nsis = NSInputStream(
      ("a" + "b".repeat(SEGMENT_SIZE * 2) + "c").encodeToByteArray().toNSData()
    )

    // Source: ab...bc
    val source: Source = nsis.source()
    val sink = Buffer()

    // Source: b...bc. Sink: abb.
    assertEquals(3, source.read(sink, 3))
    assertEquals("abb", sink.readUtf8(3))

    // Source: b...bc. Sink: b...b.
    assertEquals(SEGMENT_SIZE.toLong(), source.read(sink, 20000))
    assertEquals("b".repeat(SEGMENT_SIZE), sink.readUtf8())

    // Source: b...bc. Sink: b...bc.
    assertEquals((SEGMENT_SIZE - 1).toLong(), source.read(sink, 20000))
    assertEquals("b".repeat(SEGMENT_SIZE - 2) + "c", sink.readUtf8())

    // Source and sink are empty.
    assertEquals(-1, source.read(sink, 1))
  }

  @Test fun sourceFromInputStreamWithSegmentSize() {
    val nsis = NSInputStream(ByteArray(SEGMENT_SIZE).toNSData())
    val source = nsis.source()
    val sink = Buffer()

    assertEquals(SEGMENT_SIZE.toLong(), source.read(sink, SEGMENT_SIZE.toLong()))
    assertEquals(-1, source.read(sink, SEGMENT_SIZE.toLong()))

    assertNoEmptySegments(sink)
  }

  @Test fun sourceFromInputStreamBounds() {
    val source = NSInputStream(ByteArray(100).toNSData()).source()
    try {
      source.read(Buffer(), -1)
      fail()
    } catch (expected: IllegalArgumentException) {
    }
  }

  companion object {
    const val SEGMENT_SIZE = Segment.SIZE
  }
}
