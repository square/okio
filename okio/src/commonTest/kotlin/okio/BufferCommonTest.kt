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

import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test
import kotlin.test.assertEquals

class BufferCommonTest {

  @Test fun copyToBuffer() {
    val source = Buffer()
    source.write("party".encodeUtf8())

    val target = Buffer()
    source.copyTo(target)
    assertEquals("party", target.readByteString().utf8())
    assertEquals("party", source.readByteString().utf8())
  }

  @Test fun copyToBufferWithOffset() {
    val source = Buffer()
    source.write("party".encodeUtf8())

    val target = Buffer()
    source.copyTo(target, 2)
    assertEquals("rty", target.readByteString().utf8())
    assertEquals("party", source.readByteString().utf8())
  }

  @Test fun copyToBufferWithByteCount() {
    val source = Buffer()
    source.write("party".encodeUtf8())

    val target = Buffer()
    source.copyTo(target, 0, 3)
    assertEquals("par", target.readByteString().utf8())
    assertEquals("party", source.readByteString().utf8())
  }

  @Test fun copyToBufferWithOffsetAndByteCount() {
    val source = Buffer()
    source.write("party".encodeUtf8())

    val target = Buffer()
    source.copyTo(target, 1, 3)
    assertEquals("art", target.readByteString().utf8())
    assertEquals("party", source.readByteString().utf8())
  }

  @Test fun completeSegmentByteCountOnEmptyBuffer() {
    val buffer = Buffer()
    assertEquals(0, buffer.completeSegmentByteCount())
  }

  @Test fun completeSegmentByteCountOnBufferWithFullSegments() {
    val buffer = Buffer()
    buffer.writeUtf8("a".repeat(Segment.SIZE * 4))
    assertEquals((Segment.SIZE * 4).toLong(), buffer.completeSegmentByteCount())
  }

  @Test fun completeSegmentByteCountOnBufferWithIncompleteTailSegment() {
    val buffer = Buffer()
    buffer.writeUtf8("a".repeat(Segment.SIZE * 4 - 10))
    assertEquals((Segment.SIZE * 3).toLong(), buffer.completeSegmentByteCount())
  }
}
