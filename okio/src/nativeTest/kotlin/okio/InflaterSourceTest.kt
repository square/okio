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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InflaterSourceTest {
  @Test
  fun inflateFromSourceThatThrowsOnRead() {
    val content = randomBytes(1024 * 32)

    val throwingSource = ThrowingSource()
    deflate(throwingSource.data, content)

    val inflaterSource = throwingSource.inflate()

    val sink = Buffer()
    throwingSource.nextException = IOException("boom")
    assertFailsWith<IOException> {
      inflaterSource.read(sink, Long.MAX_VALUE)
    }
    assertEquals(0, sink.size)

    // We didn't lose any data. This isn't how real programs recover in practice, but it
    // demonstrates that no segments are unaccounted for after an exception
    assertEquals(content, inflaterSource.buffer().readByteString())
    inflaterSource.close()
    assertNoEmptySegments(throwingSource.data)
    assertNoEmptySegments(sink)
  }

  @Test
  fun inflateSourceThrowsOnClose() {
    val content = randomBytes(1024 * 32)

    val throwingSource = ThrowingSource()
    deflate(throwingSource.data, content)

    val inflaterSource = throwingSource.inflate()
    val bufferedInflaterSource = inflaterSource.buffer()
    assertEquals(content, bufferedInflaterSource.readByteString())

    throwingSource.nextException = IOException("boom")
    assertFailsWith<IOException> {
      inflaterSource.close()
    }

    assertTrue(throwingSource.closed)
    assertTrue(inflaterSource.inflater.dataProcessor.closed)
    assertNoEmptySegments(throwingSource.data)
    assertNoEmptySegments(bufferedInflaterSource.buffer)
  }

  @Test
  fun inflateInvalidThrows() {
    // Half valid deflated data + and half 0xff.
    val invalidData = Buffer()
      .apply {
        val deflatedData = Buffer()
        deflate(deflatedData, randomBytes(1024 * 32))
        write(deflatedData, deflatedData.size / 2)

        write(ByteArray(deflatedData.size.toInt() / 2) { -128 })
      }

    val inflaterSource = invalidData.inflate()
    val bufferedInflaterSource = inflaterSource.buffer()
    assertFailsWith<IOException> {
      bufferedInflaterSource.readByteString()
    }

    bufferedInflaterSource.close()
    assertTrue(inflaterSource.inflater.dataProcessor.closed)
    assertNoEmptySegments(invalidData.buffer)
    assertNoEmptySegments(bufferedInflaterSource.buffer)
  }

  /**
   * Confirm that [InflaterSource.read] doesn't read from its source stream until it's necessary
   * to do so. (When it does read from the source, it reads a full segment.)
   */
  @Test
  fun readsFromSourceDoNotOccurUntilNecessary() {
    val deflatedData = Buffer()
    deflate(deflatedData, randomBytes(1024 * 32, seed = 0))

    val inflaterSource = deflatedData.inflate()

    // These index values discovered experimentally.
    val sink = Buffer()
    inflaterSource.read(sink, 8186)
    assertEquals(24 * 1024 + 10, deflatedData.size)

    inflaterSource.read(sink, 1)
    assertEquals(24 * 1024 + 10, deflatedData.size)

    inflaterSource.read(sink, 1)
    assertEquals(16 * 1024 + 10, deflatedData.size)

    inflaterSource.read(sink, 1)
    assertEquals(16 * 1024 + 10, deflatedData.size)
  }

  @Test
  fun readsFromSourceDoNotOccurAfterExhausted() {
    val content = randomBytes(1024 * 32, seed = 0)

    val throwingSource = ThrowingSource()
    deflate(throwingSource.data, content)

    val inflaterSource = throwingSource.inflate()
    val bufferedInflaterSource = inflaterSource.buffer()

    assertEquals(content, bufferedInflaterSource.readByteString())

    throwingSource.nextException = IOException("boom")
    assertTrue(bufferedInflaterSource.exhausted()) // Doesn't throw!
    throwingSource.nextException = null

    inflaterSource.close()
  }

  @Test
  fun trailingDataIgnored() {
    val content = randomBytes(1024 * 32)

    val deflatedData = Buffer()
    deflate(deflatedData, content)
    deflatedData.write(ByteArray(1024 * 32))

    val inflaterSource = deflatedData.inflate()
    val bufferedInflaterSource = inflaterSource.buffer()

    assertEquals(content, bufferedInflaterSource.readByteString())
    assertTrue(bufferedInflaterSource.exhausted())
    assertEquals(24_586, deflatedData.size) // One trailing segment is consumed.

    inflaterSource.close()
  }

  class ThrowingSource : Source {
    val data = Buffer()
    var nextException: Throwable? = null
    var closed = false

    override fun read(sink: Buffer, byteCount: Long): Long {
      nextException?.let { nextException = null; throw it }
      return data.read(sink, byteCount)
    }

    override fun timeout() = Timeout.NONE

    override fun close() {
      closed = true
      nextException?.let { nextException = null; throw it }
    }
  }

  private fun deflate(sink: BufferedSink, content: ByteString) {
    sink.deflate().buffer().use { deflaterSink ->
      deflaterSink.write(content)
    }
  }
}
