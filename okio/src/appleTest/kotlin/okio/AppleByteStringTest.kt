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
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding

class AppleByteStringTest {
  @Test fun nsDataToByteString() {
    val data = ("Hello" as NSString).dataUsingEncoding(NSUTF8StringEncoding) as NSData

    @Suppress("DEPRECATION") // Ensure deprecated function continues to work.
    val byteStringDeprecated = data.toByteString()
    assertEquals("Hello", byteStringDeprecated.utf8())

    val byteString = with(ByteString) { data.toByteString() }
    assertEquals("Hello", byteString.utf8())
  }

  @Test fun emptyNsDataToByteString() {
    val data = ("" as NSString).dataUsingEncoding(NSUTF8StringEncoding) as NSData

    @Suppress("DEPRECATION") // Ensure deprecated function continues to work.
    val byteStringDeprecated = data.toByteString()
    assertEquals(ByteString.EMPTY, byteStringDeprecated)

    val byteString = with(ByteString) { data.toByteString() }
    assertEquals(ByteString.EMPTY, byteString)
  }
}
