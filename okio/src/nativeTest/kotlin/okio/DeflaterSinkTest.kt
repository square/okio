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

class DeflaterSinkTest {
  @Test
  fun deflateIntoSinkThatThrowsOnWrite() {
    val throwingSink = ThrowingSink()

    val content = randomBytes(1024 * 32)
    val source = Buffer().write(content)

    val deflaterSink = throwingSink.deflate()

    throwingSink.nextException = IOException("boom")
    assertFailsWith<IOException> {
      deflaterSink.write(source, source.size)
    }

    // We didn't lose any data. This isn't how real programs recover in practice, but it
    // demonstrates that no segments are unaccounted for after an exception
    deflaterSink.write(source, source.size)
    deflaterSink.close()

    assertEquals(content, inflate(throwingSink.data))
  }

  @Test
  fun deflateIntoSinkThatThrowsOnFlush() {
    val throwingSink = ThrowingSink()

    val content = randomBytes(1024 * 32)
    val source = Buffer().write(content)

    val deflaterSink = throwingSink.deflate()
    deflaterSink.write(source, source.size)

    throwingSink.nextException = IOException("boom")
    assertFailsWith<IOException> {
      deflaterSink.flush()
    }

    deflaterSink.close()

    assertEquals(content, inflate(throwingSink.data))
  }

  @Test
  fun deflateIntoSinkThatThrowsOnClose() {
    val throwingSink = ThrowingSink()

    val content = randomBytes(1024 * 32)
    val source = Buffer().write(content)

    val deflaterSink = throwingSink.deflate()
    deflaterSink.write(source, source.size)

    throwingSink.nextException = IOException("boom")
    assertFailsWith<IOException> {
      deflaterSink.close()
    }

    assertTrue(deflaterSink.deflater.dataProcessor.closed)
    assertTrue(throwingSink.closed)
  }

  class ThrowingSink : Sink {
    val data = Buffer()
    var nextException: Throwable? = null
    var closed = false

    override fun write(source: Buffer, byteCount: Long) {
      nextException?.let { nextException = null; throw it }
      data.write(source, byteCount)
    }

    override fun flush() {
      nextException?.let { nextException = null; throw it }
      data.flush()
    }

    override fun timeout() = Timeout.NONE

    override fun close() {
      closed = true
      nextException?.let { nextException = null; throw it }
    }
  }

  private fun inflate(deflated: Buffer): ByteString {
    return deflated.inflate().buffer().use { inflaterSource ->
      inflaterSource.readByteString()
    }
  }
}
