/*
 * Copyright (C) 2024 Square, Inc.
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
package okio.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class CRC32Test {
  @Test fun happyPath() {
    val crc32 = CRC32()
    crc32.update("hello world!".encodeToByteArray())
    assertEquals(0x3B4C26D, crc32.getValue())
  }

  @Test fun multipleUpdates() {
    val crc32 = CRC32()
    crc32.update("hello ".encodeToByteArray())
    crc32.update("world!".encodeToByteArray())
    assertEquals(0x3B4C26D, crc32.getValue())
  }

  @Test fun resetClearsState() {
    val crc32 = CRC32()
    crc32.update("unused".encodeToByteArray())
    crc32.reset()

    crc32.update("hello ".encodeToByteArray())
    crc32.update("world!".encodeToByteArray())
    assertEquals(0x3B4C26D, crc32.getValue())
  }

  @Test fun offsetAndByteCountAreHonored() {
    val crc32 = CRC32()
    crc32.update("well hello there".encodeToByteArray(), 5, 6)
    crc32.update("city! world! universe!".encodeToByteArray(), 6, 6)
    assertEquals(0x3B4C26D, crc32.getValue())
  }

  @Test fun emptyInput() {
    val crc32 = CRC32()
    assertEquals(0x0, crc32.getValue())
  }
}
