/*
 * Copyright (C) 2018 Square, Inc.
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

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.ByteString.Companion.encode
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.readByteString
import okio.ByteString.Companion.toByteString

class ByteStringKotlinTest {
  @Test fun arrayToByteString() {
    val actual = byteArrayOf(1, 2, 3, 4).toByteString()
    val expected = ByteString.of(1, 2, 3, 4)
    assertEquals(actual, expected)
  }

  @Test fun arraySubsetToByteString() {
    val actual = byteArrayOf(1, 2, 3, 4).toByteString(1, 2)
    val expected = ByteString.of(2, 3)
    assertEquals(actual, expected)
  }

  @Test fun byteBufferToByteString() {
    val actual = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).toByteString()
    val expected = ByteString.of(1, 2, 3, 4)
    assertEquals(actual, expected)
  }

  @Test fun stringEncodeByteStringDefaultCharset() {
    val actual = "a\uD83C\uDF69c".encode()
    val expected = "a\uD83C\uDF69c".encodeUtf8()
    assertEquals(actual, expected)
  }

  @Test fun streamReadByteString() {
    val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    val actual = stream.readByteString(4)
    val expected = ByteString.of(1, 2, 3, 4)
    assertEquals(actual, expected)
  }

  @Test fun substring() {
    val byteString = "abcdef".encodeUtf8()
    assertEquals(byteString.substring(), "abcdef".encodeUtf8())
    assertEquals(byteString.substring(endIndex = 3), "abc".encodeUtf8())
    assertEquals(byteString.substring(beginIndex = 3), "def".encodeUtf8())
    assertEquals(byteString.substring(beginIndex = 1, endIndex = 5), "bcde".encodeUtf8())
  }
}
