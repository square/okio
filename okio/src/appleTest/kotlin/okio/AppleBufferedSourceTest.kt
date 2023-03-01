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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSInputStream
import platform.darwin.NSUIntegerVar
import platform.darwin.UInt8Var

@OptIn(UnsafeNumber::class)
class AppleBufferedSourceTest {
  @Test fun bufferInputStream() {
    val source = Buffer()
    source.writeUtf8("abc")
    testInputStream(source.inputStream())
  }

  @Test fun realBufferedSourceInputStream() {
    val source = Buffer()
    source.writeUtf8("abc")
    testInputStream(RealBufferedSource(source).inputStream())
  }

  private fun testInputStream(nsis: NSInputStream) {
    nsis.open()
    val byteArray = ByteArray(4)
    byteArray.usePinned {
      val cPtr = it.addressOf(0).reinterpret<UInt8Var>()

      byteArray.fill(-5)
      assertEquals(3, nsis.read(cPtr, 4))
      assertEquals("[97, 98, 99, -5]", byteArray.contentToString())

      byteArray.fill(-7)
      assertEquals(0, nsis.read(cPtr, 4))
      assertEquals("[-7, -7, -7, -7]", byteArray.contentToString())
    }
  }

  @Test fun nsInputStreamGetBuffer() {
    val source = Buffer()
    source.writeUtf8("abc")

    val nsis = source.inputStream()
    nsis.open()
    assertTrue(nsis.hasBytesAvailable)

    memScoped {
      val bufferPtr = alloc<CPointerVar<UInt8Var>>()
      val lengthPtr = alloc<NSUIntegerVar>()
      assertTrue(nsis.getBuffer(bufferPtr.ptr, lengthPtr.ptr))

      val length = lengthPtr.value
      assertNotNull(length)
      assertEquals(3.convert(), length)

      val buffer = bufferPtr.value
      assertNotNull(buffer)
      assertEquals('a'.code.convert(), buffer[0])
      assertEquals('b'.code.convert(), buffer[1])
      assertEquals('c'.code.convert(), buffer[2])
    }
  }

  @Test fun nsInputStreamClose() {
    val buffer = Buffer()
    buffer.writeUtf8("abc")
    val source = RealBufferedSource(buffer)
    assertFalse(source.closed)

    val nsis = source.inputStream()
    nsis.open()
    nsis.close()
    assertTrue(source.closed)

    val byteArray = ByteArray(4)
    byteArray.usePinned {
      val cPtr = it.addressOf(0).reinterpret<UInt8Var>()

      byteArray.fill(-5)
      assertEquals(-1, nsis.read(cPtr, 4))
      assertNotNull(nsis.streamError)
      assertEquals("closed", nsis.streamError?.localizedDescription)
      assertEquals("[-5, -5, -5, -5]", byteArray.contentToString())
    }
  }
}
