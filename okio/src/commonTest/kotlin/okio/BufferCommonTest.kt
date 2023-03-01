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
import okio.ByteString.Companion.encodeUtf8

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

  @Test fun testHash() {
    val buffer = Buffer().apply { write("Kevin".encodeUtf8()) }
    with(buffer) {
      assertEquals("e043899daa0c7add37bc99792b2c045d6abbc6dc", sha1().hex())
      assertEquals("f1cd318e412b5f7226e5f377a9544ff7", md5().hex())
      assertEquals("0e4dd66217fc8d2e298b78c8cd9392870dcd065d0ff675d0edff5bcd227837e9", sha256().hex())
      assertEquals("483676b93c4417198b465083d196ec6a9fab8d004515874b8ff47e041f5f56303cc08179625030b8b5b721c09149a18f0f59e64e7ae099518cea78d3d83167e1", sha512().hex())
    }
  }
}
