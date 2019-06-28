/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package okio

import platform.zlib.Z_NO_COMPRESSION
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class DeflaterSinkTest {
  @Test fun deflateWithClose() {
    val data = Buffer()
    val original = "They're moving in herds. They do move in herds."
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink)
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  /*
    okio.EOFException: source exhausted prematurely
            at kfun:kotlin.Throwable.<init>(kotlin.String?)kotlin.Throwable (0000000000402140)
            at kfun:kotlin.Exception.<init>(kotlin.String?)kotlin.Exception (00000000004020f0)
            at kfun:okio.IOException.<init>(kotlin.String?)okio.IOException (000000000040eff0)
            at kfun:okio.EOFException.<init>(kotlin.String?)okio.EOFException (000000000040efd0)
            at kfun:okio.InflaterSource.read(okio.Buffer;kotlin.Long)kotlin.Long (000000000040a8b0)
            at kfun:okio.DeflaterSinkTest.readAll#internal (00000000004960c0)
            at kfun:okio.DeflaterSinkTest.inflate#internal (0000000000495f70)
            at kfun:okio.DeflaterSinkTest.deflateWithSyncFlush() (0000000000496970)
            at kfun:okio.$DeflaterSinkTest$test$0.$deflateWithSyncFlush$FUNCTION_REFERENCE$133.invoke#internal (0000000000496940)
            at kfun:okio.$DeflaterSinkTest$test$0.$deflateWithSyncFlush$FUNCTION_REFERENCE$133.$<bridge-UNNN>invoke(#GENERIC)#internal (0000000000496910)
            at kfun:kotlin.native.internal.test.BaseClassSuite.TestCase.run() (0000000000489160)
            at kfun:kotlin.native.internal.test.TestRunner.run#internal (00000000004c7320)
            at kfun:kotlin.native.internal.test.TestRunner.runIteration#internal (00000000004c62f0)
            at kfun:kotlin.native.internal.test.TestRunner.run()kotlin.Int (00000000004c5d30)
            at kfun:kotlin.native.internal.test.testLauncherEntryPoint(kotlin.Array<kotlin.String>)kotlin.Int (00000000004c4d10)
            at kfun:kotlin.native.internal.test.main(kotlin.Array<kotlin.String>) (00000000004c4cf0)
            at Konan_start (00000000004c4c40)
            at Init_and_run_start (00000000004c4ab0)
            at __tmainCRTStartup (0000000000401180)
            at mainCRTStartup (00000000004014e0)
            at  (00007ffdfa3e4020)
            at  (00007ffdfb903670)
   */
  @Ignore // TODO fix me!
  @Test fun deflateWithSyncFlush() {
    val original = "Yes, yes, yes. That's why we're taking extreme precautions."
    val data = Buffer()
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink)
    deflaterSink.write(data, data.size)
    deflaterSink.flush()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test fun deflateWellCompressed() {
    val original = 'a'.repeat(1024 * 1024)
    val data = Buffer()
    data.writeUtf8(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink)
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readUtf8())
  }

  @Test fun deflatePoorlyCompressed() {
    val original = randomBytes(1024 * 1024)
    val data = Buffer()
    data.write(original)
    val sink = Buffer()
    val deflaterSink = DeflaterSink(sink)
    deflaterSink.write(data, data.size)
    deflaterSink.close()
    val inflated = inflate(sink)
    assertEquals(original, inflated.readByteString())
  }

  @Test fun multipleSegmentsWithoutCompression() {
    val buffer = Buffer()
    val deflater = Deflater(Z_NO_COMPRESSION)
    val deflaterSink = DeflaterSink(buffer, deflater)
    val byteCount = Segment.SIZE * 4
    deflaterSink.write(Buffer().writeUtf8('a'.repeat(byteCount)), byteCount.toLong())
    deflaterSink.close()
    assertEquals('a'.repeat(byteCount), inflate(buffer).readUtf8(byteCount.toLong()))
  }

  @Test fun deflateIntoNonemptySink() {
    val original = "They're moving in herds. They do move in herds."

    // Exercise all possible offsets for the outgoing segment.
    for (i in 0 until Segment.SIZE) {
      val data = Buffer().writeUtf8(original)
      val sink = Buffer().writeUtf8('a'.repeat(i))

      val deflaterSink = DeflaterSink(sink)
      deflaterSink.write(data, data.size)
      deflaterSink.close()

      sink.skip(i.toLong())
      val inflated = inflate(sink)
      assertEquals(original, inflated.readUtf8())
    }
  }

  /**
   * This test deflates a single segment of without compression because that's
   * the easiest way to force close() to emit a large amount of data to the
   * underlying sink.
   */
  @Ignore // TODO deflaterSink is not buffered
  @Test fun closeWithExceptionWhenWritingAndClosing() {
    val mockSink = MockSink()
    mockSink.scheduleThrow(0, IOException("first"))
    mockSink.scheduleThrow(1, IOException("second"))
    val deflater = Deflater(Z_NO_COMPRESSION)
    val deflaterSink = DeflaterSink(mockSink, deflater)
    deflaterSink.write(Buffer().writeUtf8('a'.repeat(Segment.SIZE)), Segment.SIZE.toLong())
    try {
      deflaterSink.close()
      fail()
    } catch (expected: IOException) {
      assertEquals("first", expected.message)
    }

    mockSink.assertLogContains("close()")
  }

  /** Returns a new buffer containing the inflated contents of `deflated`.  */
  private fun inflate(deflated: Buffer): Buffer {
    val result = Buffer()
    val source = InflaterSource(deflated)
    source.readAll(result)
    return result
  }

  private fun Source.readAll(sink: Buffer) {
    while (read(sink, Int.MAX_VALUE.toLong()) != -1L) {
    }
  }
}
