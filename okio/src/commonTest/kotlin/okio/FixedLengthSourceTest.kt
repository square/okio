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
import kotlin.test.assertFails

class FixedLengthSourceTest2 {
  @Test fun byteCountNotNegative() {
    val source = Buffer()
    val t = assertFails {
      source.limit(-2)
    }
    assertEquals(IllegalArgumentException::class, t::class)
    assertEquals("byteCount < 0: -2", t.message)
  }

  @Test fun zeroLimit() {
    val source = Buffer().writeUtf8("they're moving in herds")
    val destination = Buffer()

    var callbacks = 0
    val limited = source.limit(0) { eof ->
      callbacks += if (eof) -1 else 1
    }

    // We don't want the creation of the zero-length limit to synchronously trigger the callback
    // or read any data.
    assertEquals(0, callbacks)
    assertEquals(23, source.size)

    // First read triggers callback, moves no data.
    assertEquals(-1, limited.read(destination, 10))
    assertEquals(1, callbacks)
    assertEquals(23, source.size)
    assertEquals(0, destination.size)

    // Subsequent reads do not trigger callback, also move no data.
    assertEquals(-1, limited.read(destination, 10))
    assertEquals(1, callbacks)
    assertEquals(23, source.size)
    assertEquals(0, destination.size)
  }
}
