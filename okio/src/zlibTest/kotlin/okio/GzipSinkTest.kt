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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class GzipSinkTest {
  @Test
  fun gzipGunzip() {
    val data = Buffer()
    val original = "It's a UNIX system! I know this!"
    data.writeUtf8(original)
    val sink = Buffer()
    val gzipSink = GzipSink(sink)
    gzipSink.write(data, data.size)
    gzipSink.close()
    val inflated = gunzip(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test
  fun closeWithExceptionWhenWritingAndClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, IOException("first"))
    mockSink.scheduleThrow(1, IOException("second"))
    val gzipSink = GzipSink(mockSink)
    gzipSink.write(Buffer().writeUtf8("a".repeat(Segment.SIZE)), Segment.SIZE.toLong())
    try {
      gzipSink.close()
      fail()
    } catch (expected: IOException) {
      assertEquals("first", expected.message)
    }
    mockSink.assertLogContains("close()")
  }

  private fun gunzip(gzipped: Buffer): Buffer {
    val result = Buffer()
    val source = GzipSource(gzipped)
    while (source.read(result, Int.MAX_VALUE.toLong()) != -1L) {
    }
    return result
  }
}
