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
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.NSOutputStream
import platform.Foundation.NSStreamDataWrittenToMemoryStreamKey
import platform.Foundation.outputStreamToMemory

class NSOutputStreamSinkTest {
  @Test
  @OptIn(UnsafeNumber::class)
  fun nsOutputStreamSink() {
    val out = NSOutputStream.outputStreamToMemory()
    val sink = out.sink()
    val buffer = Buffer().apply {
      writeUtf8("a")
    }
    sink.write(buffer, 1L)
    val data = out.propertyForKey(NSStreamDataWrittenToMemoryStreamKey) as NSData
    assertEquals(1U, data.length)
    val bytes = data.bytes!!.reinterpret<ByteVar>()
    assertEquals(0x61, bytes[0])
  }

  @Test
  fun sinkFromOutputStream() {
    val data = Buffer().apply {
      writeUtf8("a")
      writeUtf8("b".repeat(9998))
      writeUtf8("c")
    }
    val out = NSOutputStream.outputStreamToMemory()
    val sink = out.sink()

    sink.write(data, 3)
    val outData = out.propertyForKey(NSStreamDataWrittenToMemoryStreamKey) as NSData
    val outString = outData.toByteArray().decodeToString()
    assertEquals("abb", outString)

    sink.write(data, data.size)
    val outData2 = out.propertyForKey(NSStreamDataWrittenToMemoryStreamKey) as NSData
    val outString2 = outData2.toByteArray().decodeToString()
    assertEquals("a" + "b".repeat(9998) + "c", outString2)
  }
}
