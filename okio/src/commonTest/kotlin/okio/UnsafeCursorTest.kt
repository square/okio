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
import kotlin.test.assertTrue

class UnsafeCursorTest {
  @Test fun acquireForRead() {
    val buffer = Buffer()
    buffer.writeUtf8("xo".repeat(5000))

    val cursor = buffer.readAndWriteUnsafe()
    try {
      val copy = Buffer()
      while (cursor.next() != -1) {
        copy.write(cursor.data!!, cursor.start, cursor.end - cursor.start)
      }
    } finally {
      cursor.close()
    }

    assertEquals("xo".repeat(5000), buffer.readUtf8())
  }

  @Test fun acquireForWrite() {
    val buffer = Buffer()
    buffer.writeUtf8("xo".repeat(5000))

    val cursor = buffer.readAndWriteUnsafe()
    try {
      while (cursor.next() != -1) {
        cursor.data!!.fill('z'.code.toByte(), cursor.start, cursor.end)
      }
    } finally {
      cursor.close()
    }

    assertEquals("zz".repeat(5000), buffer.readUtf8())
  }

  @Test fun expand() {
    val buffer = Buffer()

    val cursor = buffer.readAndWriteUnsafe()
    try {
      cursor.expandBuffer(100)
      cursor.data!!.fill('z'.code.toByte(), cursor.start, cursor.start + 100)
      cursor.resizeBuffer(100L)
    } finally {
      cursor.close()
    }

    val expected = "z".repeat(100)
    val actual = buffer.readUtf8()
    println(actual)
    println(expected)
    assertEquals(expected, actual)
  }

  @Test fun resizeBuffer() {
    val buffer = Buffer()

    val cursor = buffer.readAndWriteUnsafe()
    try {
      cursor.resizeBuffer(100L)
      cursor.data!!.fill('z'.code.toByte(), cursor.start, cursor.end)
    } finally {
      cursor.close()
    }

    assertEquals("z".repeat(100), buffer.readUtf8())
  }

  @Test fun testUnsafeCursorIsClosable() {
    assertTrue(Closeable::class.isInstance(Buffer.UnsafeCursor()))
  }
}
