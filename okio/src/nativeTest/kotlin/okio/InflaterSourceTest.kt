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

    val inflaterSource = InflaterSource(throwingSource)

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
  }

  @Test
  fun inflateSourceThrowsOnClose() {
    val content = randomBytes(1024 * 32)

    val throwingSource = ThrowingSource()
    deflate(throwingSource.data, content)

    val inflaterSource = InflaterSource(throwingSource)
    assertEquals(content, inflaterSource.buffer().readByteString())

    throwingSource.nextException = IOException("boom")
    assertFailsWith<IOException> {
      inflaterSource.close()
    }

    assertTrue(throwingSource.closed)
    assertTrue(inflaterSource.inflater.closed)
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
    DeflaterSink(sink).buffer().use { deflaterSink ->
      deflaterSink.write(content)
    }
  }
}
