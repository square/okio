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
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.darwin.UInt8Var

class AppleBufferedSourceTest {
  @Test fun bufferInputStream() {
    val source = Buffer()
    source.writeUtf8("abc")

    val byteArray = ByteArray(4)
    byteArray.usePinned {
      val cPtr = it.addressOf(0).reinterpret<UInt8Var>()

      byteArray.fill(-5)
      val nsis = source.inputStream()
      assertEquals(3, nsis.read(cPtr, 4).toLong())
      assertEquals("[97, 98, 99, -5]", byteArray.contentToString())

      byteArray.fill(-7)
      assertEquals(0, nsis.read(cPtr, 4).toLong())
      assertEquals("[-7, -7, -7, -7]", byteArray.contentToString())
    }
  }
}
