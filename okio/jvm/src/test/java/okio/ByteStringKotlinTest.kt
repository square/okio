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

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encode
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.readByteString
import okio.ByteString.Companion.toByteString
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import kotlin.test.fail


/**
 * ```actual shouldBe expected```
 *  as a replacement for junit style's assert
 *
 * Inspired by kotlintest which has a ton of other nice matchers
 * https://github.com/kotlintest/kotlintest/blob/master/doc/matchers.md
 */
infix fun <T> T.shouldBe(any: Any?) {
  if ((this == null && any != null) || this != any)
    throw AssertionError( "expected: $any but was: $this")
}

class ByteStringKotlinTest {
  @Test fun get() {
    val actual = "abc".encodeUtf8()
    actual[0] shouldBe 'a'.toByte()
    actual[1] shouldBe 'b'.toByte()
    actual[2] shouldBe 'c'.toByte()
    try {
      actual[-1]
      fail()
    } catch (expected: IndexOutOfBoundsException) {
    }
    try {
      actual[3]
      fail()
    } catch (expected: IndexOutOfBoundsException) {
    }
  }

  @Test fun decodeBase64() {
    "YfCfjalj".decodeBase64() shouldBe "a\uD83C\uDF69c".encodeUtf8()
  }

  @Test fun decodeBase64Invalid() {
    ";-)".decodeBase64() shouldBe null
  }

  @Test fun decodeHex() {
    "CAFEBABE".decodeHex() shouldBe ByteString.of(-54, -2, -70, -66)
  }

  @Test fun arrayToByteString() {
    byteArrayOf(1, 2, 3, 4).toByteString() shouldBe ByteString.of(1, 2, 3, 4)
  }

  @Test fun arraySubsetToByteString() {
    val actual = byteArrayOf(1, 2, 3, 4).toByteString(1, 2)
    val expected = ByteString.of(2, 3)
    actual shouldBe expected
  }

  @Test fun byteBufferToByteString() {
    val actual = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).toByteString()
    val expected = ByteString.of(1, 2, 3, 4)
    actual shouldBe expected
  }

  @Test fun stringEncodeByteStringDefaultCharset() {
    "a\uD83C\uDF69c".encode() shouldBe "a\uD83C\uDF69c".encodeUtf8()
  }

  @Test fun streamReadByteString() {
    val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    val actual = stream.readByteString(4)
    val expected = ByteString.of(1, 2, 3, 4)
    actual shouldBe expected
  }

  @Test fun substring() {
    val byteString = "abcdef".encodeUtf8()
    byteString.substring() shouldBe "abcdef".encodeUtf8()
    byteString.substring(endIndex= 3) shouldBe "abc".encodeUtf8()
    byteString.substring(beginIndex = 3) shouldBe "def".encodeUtf8()
    byteString.substring(beginIndex = 1, endIndex = 5) shouldBe "bcde".encodeUtf8()
  }

}