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
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding

class AppleBufferTest {
  @Test fun readAndWriteNSData() {
    val ab = "ab".toNSData()
    val cdef = "cdef".toNSData()
    val abcd = "abcd".toNSData()
    val ef = "ef".toNSData()

    val buffer = Buffer()
    buffer.write(ab)
    assertEquals(2, buffer.size)
    buffer.write(cdef)
    assertEquals(6, buffer.size)

    assertEquals(abcd, buffer.readNSData(4uL))
    assertEquals(2, buffer.size)
    assertEquals(ef, buffer.readNSData(2uL))
    assertEquals(0, buffer.size)
    assertFailsWith<EOFException> {
      buffer.readNSData(1uL)
    }
  }

  @Test fun multipleSegmentBuffers() {
    val buffer = Buffer()
    buffer.write('a'.repeat(1000).toNSData())
    buffer.write('b'.repeat(2500).toNSData())
    buffer.write('c'.repeat(5000).toNSData())
    buffer.write('d'.repeat(10000).toNSData())
    buffer.write('e'.repeat(25000).toNSData())
    buffer.write('f'.repeat(50000).toNSData())

    assertEquals('a'.repeat(999), buffer.readUtf8(999)) // a...a
    assertEquals("a" + 'b'.repeat(2500) + "c", buffer.readUtf8(2502)) // ab...bc
    assertEquals('c'.repeat(4998), buffer.readUtf8(4998)) // c...c
    assertEquals("c" + 'd'.repeat(10000) + "e", buffer.readUtf8(10002)) // cd...de
    assertEquals('e'.repeat(24998), buffer.readUtf8(24998)) // e...e
    assertEquals("e" + 'f'.repeat(50000), buffer.readUtf8(50001)) // ef...f
    assertEquals(0, buffer.size)
  }

  private fun String.toNSData(): NSData {
    return (this as NSString).dataUsingEncoding(NSUTF8StringEncoding) as NSData
  }
}
