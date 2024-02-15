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
package okio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.value
import okio.ByteString.Companion.EMPTY
import okio.ByteString.Companion.encodeUtf8
import platform.posix.uint8_tVar

class ByteStringCinteropTest {
  @Test fun pointerToByteStringZeroDoesNotRead() = memScoped {
    val pointer = allocArray<uint8_tVar>(0)
    val bytes = pointer.readByteString(0)
    // Can't find a way to determine that readBytes was not called, so assume that if EMPTY was
    // returned there was a short-circuit.
    assertSame(EMPTY, bytes)
  }

  @Test fun pointerToByteString() = memScoped {
    val pointer = allocArray<uint8_tVar>(26L) { index ->
      value = ('a'.code + index).toUByte()
    }
    val bytes = pointer.plus(5)!!.readByteString(15)
    assertEquals("fghijklmnopqrst".encodeUtf8(), bytes)
  }
}
