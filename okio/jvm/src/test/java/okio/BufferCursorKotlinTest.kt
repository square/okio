/*
 * Copyright (C) 2018 Square, Inc.
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

import okio.Buffer.UnsafeCursor
import okio.TestUtil.deepCopy
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.util.Arrays
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
@Ignore("Won't run with Kotlin Gradle MPP 1.3")
class BufferCursorKotlinTest {
  companion object {
    @Parameters(name = "{0}")
    @JvmStatic
    fun parameters() = BufferCursorTest.parameters()
  }

  @Parameter lateinit var bufferFactory: BufferCursorTest.BufferFactory

  @Test fun acquireReadOnlyDoesNotCopySharedDataArray() {
    val buffer = deepCopy(bufferFactory.newBuffer())
    assumeTrue(buffer.size > 0L)

    val shared = buffer.clone()
    assertTrue(buffer.head!!.shared)

    buffer.readUnsafe().use { cursor ->
      cursor.seek(0)
      assertSame(cursor.data, shared.head!!.data)
    }
  }

  @Test fun acquireReadWriteDoesNotCopyUnsharedDataArray() {
    val buffer = deepCopy(bufferFactory.newBuffer())
    assumeTrue(buffer.size > 0L)
    assertFalse(buffer.head!!.shared)

    val originalData = buffer.head!!.data

    buffer.readAndWriteUnsafe().use { cursor ->
      cursor.seek(0)
      assertSame(cursor.data, originalData)
    }
  }

  @Test fun acquireReadWriteCopiesSharedDataArray() {
    val buffer = deepCopy(bufferFactory.newBuffer())
    assumeTrue(buffer.size > 0L)

    val shared = buffer.clone()
    assertTrue(buffer.head!!.shared)

    buffer.readAndWriteUnsafe().use { cursor ->
      cursor.seek(0)
      assertNotSame(cursor.data, shared.head!!.data)
    }
  }

  @Test fun writeSharedSegments() {
    val buffer = bufferFactory.newBuffer()

    // Make a deep copy. This buffer's segments are not shared.
    val deepCopy = deepCopy(buffer)
    assertTrue(deepCopy.head == null || !deepCopy.head!!.shared)

    // Make a shallow copy. Both buffers' segments are shared as a side effect.
    val shallowCopy = buffer.clone()
    assertTrue(shallowCopy.head == null || shallowCopy.head!!.shared)
    assertTrue(buffer.head == null || buffer.head!!.shared)

    val expected = Buffer()
    expected.writeUtf8(TestUtil.repeat('x', buffer.size.toInt()))

    buffer.readAndWriteUnsafe().use { cursor ->
      while (cursor.next() != -1) {
        Arrays.fill(cursor.data!!, cursor.start, cursor.end, 'x'.toByte())
      }
    }

    // The buffer was fully changed.
    assertEquals(expected, buffer)

    // The buffer we're shared with is unchanged.
    assertEquals(deepCopy, shallowCopy)
  }

  /** As an optimization it's okay to use the same cursor on multiple buffers.  */
  @Test fun cursorReuse() {
    val cursor = UnsafeCursor()

    val buffer1 = bufferFactory.newBuffer()
    buffer1.readUnsafe(cursor)
    assertSame(buffer1, cursor.buffer)
    assertFalse(cursor.readWrite)
    cursor.close()
    assertSame(null, cursor.buffer)

    val buffer2 = bufferFactory.newBuffer()
    buffer2.readAndWriteUnsafe(cursor)
    assertSame(buffer2, cursor.buffer)
    assertTrue(cursor.readWrite)
    cursor.close()
    assertSame(null, cursor.buffer)
  }
}