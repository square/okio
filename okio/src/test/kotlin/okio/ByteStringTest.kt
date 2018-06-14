/*
 * Copyright (C) 2018 Square, Inc.
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

import okio.ByteString.Companion.decodeHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByteStringTest {
  @Test fun getByte() {
    val byteString = "ab12".decodeHex()
    assertEquals(-85, byteString[0].toLong())
    assertEquals(18, byteString[1].toLong())
  }

  @Test fun startsWithByteString() {
    val byteString = "112233".decodeHex()
    assertTrue(byteString.startsWith("".decodeHex()))
    assertTrue(byteString.startsWith("11".decodeHex()))
    assertTrue(byteString.startsWith("1122".decodeHex()))
    assertTrue(byteString.startsWith("112233".decodeHex()))
    assertFalse(byteString.startsWith("2233".decodeHex()))
    assertFalse(byteString.startsWith("11223344".decodeHex()))
    assertFalse(byteString.startsWith("112244".decodeHex()))
  }

  @Test fun endsWithByteString() {
    val byteString = "112233".decodeHex()
    assertTrue(byteString.endsWith("".decodeHex()))
    assertTrue(byteString.endsWith("33".decodeHex()))
    assertTrue(byteString.endsWith("2233".decodeHex()))
    assertTrue(byteString.endsWith("112233".decodeHex()))
    assertFalse(byteString.endsWith("1122".decodeHex()))
    assertFalse(byteString.endsWith("00112233".decodeHex()))
    assertFalse(byteString.endsWith("002233".decodeHex()))
  }

  @Test fun startsWithByteArray() {
    val byteString = "112233".decodeHex()
    assertTrue(byteString.startsWith("".decodeHex().toByteArray()))
    assertTrue(byteString.startsWith("11".decodeHex().toByteArray()))
    assertTrue(byteString.startsWith("1122".decodeHex().toByteArray()))
    assertTrue(byteString.startsWith("112233".decodeHex().toByteArray()))
    assertFalse(byteString.startsWith("2233".decodeHex().toByteArray()))
    assertFalse(byteString.startsWith("11223344".decodeHex().toByteArray()))
    assertFalse(byteString.startsWith("112244".decodeHex().toByteArray()))
  }

  @Test fun endsWithByteArray() {
    val byteString = "112233".decodeHex()
    assertTrue(byteString.endsWith("".decodeHex().toByteArray()))
    assertTrue(byteString.endsWith("33".decodeHex().toByteArray()))
    assertTrue(byteString.endsWith("2233".decodeHex().toByteArray()))
    assertTrue(byteString.endsWith("112233".decodeHex().toByteArray()))
    assertFalse(byteString.endsWith("1122".decodeHex().toByteArray()))
    assertFalse(byteString.endsWith("00112233".decodeHex().toByteArray()))
    assertFalse(byteString.endsWith("002233".decodeHex().toByteArray()))
  }

  @Test fun indexOfByteString() {
    val byteString = "112233".decodeHex()
    assertEquals(0, byteString.indexOf("112233".decodeHex()).toLong())
    assertEquals(0, byteString.indexOf("1122".decodeHex()).toLong())
    assertEquals(0, byteString.indexOf("11".decodeHex()).toLong())
    assertEquals(0, byteString.indexOf("11".decodeHex(), 0).toLong())
    assertEquals(0, byteString.indexOf("".decodeHex()).toLong())
    assertEquals(0, byteString.indexOf("".decodeHex(), 0).toLong())
    assertEquals(1, byteString.indexOf("2233".decodeHex()).toLong())
    assertEquals(1, byteString.indexOf("22".decodeHex()).toLong())
    assertEquals(1, byteString.indexOf("22".decodeHex(), 1).toLong())
    assertEquals(1, byteString.indexOf("".decodeHex(), 1).toLong())
    assertEquals(2, byteString.indexOf("33".decodeHex()).toLong())
    assertEquals(2, byteString.indexOf("33".decodeHex(), 2).toLong())
    assertEquals(2, byteString.indexOf("".decodeHex(), 2).toLong())
    assertEquals(3, byteString.indexOf("".decodeHex(), 3).toLong())
    assertEquals(-1, byteString.indexOf("112233".decodeHex(), 1).toLong())
    assertEquals(-1, byteString.indexOf("44".decodeHex()).toLong())
    assertEquals(-1, byteString.indexOf("11223344".decodeHex()).toLong())
    assertEquals(-1, byteString.indexOf("112244".decodeHex()).toLong())
    assertEquals(-1, byteString.indexOf("112233".decodeHex(), 1).toLong())
    assertEquals(-1, byteString.indexOf("2233".decodeHex(), 2).toLong())
    assertEquals(-1, byteString.indexOf("33".decodeHex(), 3).toLong())
    assertEquals(-1, byteString.indexOf("".decodeHex(), 4).toLong())
  }

  @Test fun indexOfWithOffset() {
    val byteString = "112233112233".decodeHex()
    assertEquals(0, byteString.indexOf("112233".decodeHex(), -1).toLong())
    assertEquals(0, byteString.indexOf("112233".decodeHex(), 0).toLong())
    assertEquals(0, byteString.indexOf("112233".decodeHex()).toLong())
    assertEquals(3, byteString.indexOf("112233".decodeHex(), 1).toLong())
    assertEquals(3, byteString.indexOf("112233".decodeHex(), 2).toLong())
    assertEquals(3, byteString.indexOf("112233".decodeHex(), 3).toLong())
    assertEquals(-1, byteString.indexOf("112233".decodeHex(), 4).toLong())
  }

  @Test fun indexOfByteArray() {
    val byteString = "112233".decodeHex()
    assertEquals(0, byteString.indexOf("112233".decodeHex().toByteArray()).toLong())
    assertEquals(1, byteString.indexOf("2233".decodeHex().toByteArray()).toLong())
    assertEquals(2, byteString.indexOf("33".decodeHex().toByteArray()).toLong())
    assertEquals(-1, byteString.indexOf("112244".decodeHex().toByteArray()).toLong())
  }
}
