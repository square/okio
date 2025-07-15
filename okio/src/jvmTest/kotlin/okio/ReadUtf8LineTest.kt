/*
 * Copyright (C) 2014 Square, Inc.
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

import app.cash.burst.Burst
import java.io.EOFException
import okio.TestUtil.SEGMENT_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@Burst
class ReadUtf8LineTest(
  factory: Factory,
) {
  enum class Factory {
    BasicBuffer {
      override fun create(data: Buffer) = data
    },
    Buffered {
      override fun create(data: Buffer): BufferedSource = RealBufferedSource(data)
    },
    SlowBuffered {
      override fun create(data: Buffer): BufferedSource {
        return RealBufferedSource(
          object : ForwardingSource(data) {
            override fun read(sink: Buffer, byteCount: Long): Long {
              return super.read(sink, 1L.coerceAtMost(byteCount))
            }
          },
        )
      }
    },
    ;

    abstract fun create(data: Buffer): BufferedSource
  }

  private val data: Buffer = Buffer()
  private val source: BufferedSource = factory.create(data)

  @Test
  fun readLines() {
    data.writeUtf8("abc\ndef\n")
    assertEquals("abc", source.readUtf8LineStrict())
    assertEquals("def", source.readUtf8LineStrict())
    try {
      source.readUtf8LineStrict()
      fail()
    } catch (expected: EOFException) {
      assertEquals("\\n not found: limit=0 content=…", expected.message)
    }
  }

  @Test
  fun readUtf8LineStrictWithLimits() {
    val lens = intArrayOf(1, SEGMENT_SIZE - 2, SEGMENT_SIZE - 1, SEGMENT_SIZE, SEGMENT_SIZE * 10)
    for (len in lens) {
      data.writeUtf8("a".repeat(len)).writeUtf8("\n")
      assertEquals(len.toLong(), source.readUtf8LineStrict(len.toLong()).length.toLong())
      source.readUtf8()
      data.writeUtf8("a".repeat(len)).writeUtf8("\n").writeUtf8("a".repeat(len))
      assertEquals(len.toLong(), source.readUtf8LineStrict(len.toLong()).length.toLong())
      source.readUtf8()
      data.writeUtf8("a".repeat(len)).writeUtf8("\r\n")
      assertEquals(len.toLong(), source.readUtf8LineStrict(len.toLong()).length.toLong())
      source.readUtf8()
      data.writeUtf8("a".repeat(len)).writeUtf8("\r\n").writeUtf8("a".repeat(len))
      assertEquals(len.toLong(), source.readUtf8LineStrict(len.toLong()).length.toLong())
      source.readUtf8()
    }
  }

  @Test
  fun readUtf8LineStrictNoBytesConsumedOnFailure() {
    data.writeUtf8("abc\n")
    try {
      source.readUtf8LineStrict(2)
      fail()
    } catch (expected: EOFException) {
      assertTrue(expected.message!!.startsWith("\\n not found: limit=2 content=61626"))
    }
    assertEquals("abc", source.readUtf8LineStrict(3))
  }

  @Test
  fun readUtf8LineStrictEmptyString() {
    data.writeUtf8("\r\nabc")
    assertEquals("", source.readUtf8LineStrict(0))
    assertEquals("abc", source.readUtf8())
  }

  @Test
  fun readUtf8LineStrictNonPositive() {
    data.writeUtf8("\r\n")
    try {
      source.readUtf8LineStrict(-1)
      fail("Expected failure: limit must be greater than 0")
    } catch (expected: IllegalArgumentException) {
    }
  }

  @Test
  fun eofExceptionProvidesLimitedContent() {
    data.writeUtf8("aaaaaaaabbbbbbbbccccccccdddddddde")
    try {
      source.readUtf8LineStrict()
      fail()
    } catch (expected: EOFException) {
      assertEquals(
        "\\n not found: limit=33 content=616161616161616162626262626262626363636363636363" +
          "6464646464646464…",
        expected.message,
      )
    }
  }

  @Test
  fun newlineAtEnd() {
    data.writeUtf8("abc\n")
    assertEquals("abc", source.readUtf8LineStrict(3))
    assertTrue(source.exhausted())
    data.writeUtf8("abc\r\n")
    assertEquals("abc", source.readUtf8LineStrict(3))
    assertTrue(source.exhausted())
    data.writeUtf8("abc\r")
    try {
      source.readUtf8LineStrict(3)
      fail()
    } catch (expected: EOFException) {
      assertEquals("\\n not found: limit=3 content=6162630d…", expected.message)
    }
    source.readUtf8()
    data.writeUtf8("abc")
    try {
      source.readUtf8LineStrict(3)
      fail()
    } catch (expected: EOFException) {
      assertEquals("\\n not found: limit=3 content=616263…", expected.message)
    }
  }

  @Test
  fun emptyLines() {
    data.writeUtf8("\n\n\n")
    assertEquals("", source.readUtf8LineStrict())
    assertEquals("", source.readUtf8LineStrict())
    assertEquals("", source.readUtf8LineStrict())
    assertTrue(source.exhausted())
  }

  @Test
  fun crDroppedPrecedingLf() {
    data.writeUtf8("abc\r\ndef\r\nghi\rjkl\r\n")
    assertEquals("abc", source.readUtf8LineStrict())
    assertEquals("def", source.readUtf8LineStrict())
    assertEquals("ghi\rjkl", source.readUtf8LineStrict())
  }

  @Test
  fun bufferedReaderCompatible() {
    data.writeUtf8("abc\ndef")
    assertEquals("abc", source.readUtf8Line())
    assertEquals("def", source.readUtf8Line())
    assertNull(source.readUtf8Line())
  }

  @Test
  fun bufferedReaderCompatibleWithTrailingNewline() {
    data.writeUtf8("abc\ndef\n")
    assertEquals("abc", source.readUtf8Line())
    assertEquals("def", source.readUtf8Line())
    assertNull(source.readUtf8Line())
  }
}
