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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Path.Companion.toPath
import platform.Foundation.NSInputStream
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID

class NSInputStreamSourceTest {
  @Test
  fun nsInputStreamSource() {
    val input = NSInputStream(data = byteArrayOf(0x61).toNSData())
    val source = input.source()
    val buffer = Buffer()
    assertEquals(1, source.read(buffer, 1L))
    assertEquals("a", buffer.readUtf8())
  }

  @OptIn(ExperimentalStdlibApi::class)
  @Test
  fun nsInputStreamSourceFromFile() {
    // can be replaced with createTempFile() when #183 is fixed
    // https://github.com/Kotlin/kotlinx-io/issues/183
    val file = "${NSTemporaryDirectory()}${NSUUID().UUIDString()}"
    try {
      FileSystem.SYSTEM.write(file.toPath()) {
        writeUtf8("example")
      }

      val input = NSInputStream(uRL = NSURL.fileURLWithPath(file))
      val source = input.source()
      val buffer = Buffer()
      assertEquals(7, source.read(buffer, 10))
      assertEquals("example", buffer.readUtf8())
    } finally {
      FileSystem.SYSTEM.delete(file.toPath(), false)
    }
  }

  @Test
  fun sourceFromInputStream() {
    val input = NSInputStream(data = ("a" + "b".repeat(Segment.SIZE * 2) + "c").encodeToByteArray().toNSData())

    // Source: ab...bc
    val source: Source = input.source()
    val sink = Buffer()

    // Source: b...bc. Sink: abb.
    assertEquals(3, source.read(sink, 3))
    assertEquals("abb", sink.readUtf8(3))

    // Source: b...bc. Sink: b...b.
    assertEquals(Segment.SIZE.toLong(), source.read(sink, 20000))
    assertEquals("b".repeat(Segment.SIZE), sink.readUtf8())

    // Source: b...bc. Sink: b...bc.
    assertEquals((Segment.SIZE - 1).toLong(), source.read(sink, 20000))
    assertEquals("b".repeat(Segment.SIZE - 2) + "c", sink.readUtf8())

    // Source and sink are empty.
    assertEquals(-1, source.read(sink, 1))
  }

  @Test
  fun sourceFromInputStreamWithSegmentSize() {
    val input = NSInputStream(data = ByteArray(Segment.SIZE).toNSData())
    val source = input.source()
    val sink = Buffer()

    assertEquals(Segment.SIZE.toLong(), source.read(sink, Segment.SIZE.toLong()))
    assertEquals(-1, source.read(sink, Segment.SIZE.toLong()))

    assertNoEmptySegments(sink)
  }

  @Test
  fun sourceFromInputStreamBounds() {
    val source = NSInputStream(data = ByteArray(100).toNSData()).source()
    assertFailsWith<IllegalArgumentException> { source.read(Buffer(), -1) }
  }
}
