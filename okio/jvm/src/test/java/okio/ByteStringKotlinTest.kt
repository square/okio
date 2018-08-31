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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import kotlin.test.fail

class ByteStringKotlinTest {
  @Test fun get() {
    val actual = "abc".encodeUtf8()
    assertThat(actual[0]).isEqualTo('a'.toByte())
    assertThat(actual[1]).isEqualTo('b'.toByte())
    assertThat(actual[2]).isEqualTo('c'.toByte())
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
    val actual = "YfCfjalj".decodeBase64()
    val expected = "a\uD83C\uDF69c".encodeUtf8()
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun decodeBase64Invalid() {
    val actual = ";-)".decodeBase64()
    assertThat(actual).isNull()
  }

  @Test fun decodeHex() {
    val actual = "CAFEBABE".decodeHex()
    val expected = ByteString.of(-54, -2, -70, -66)
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun arrayToByteString() {
    val actual = byteArrayOf(1, 2, 3, 4).toByteString()
    val expected = ByteString.of(1, 2, 3, 4)
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun arraySubsetToByteString() {
    val actual = byteArrayOf(1, 2, 3, 4).toByteString(1, 2)
    val expected = ByteString.of(2, 3)
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun byteBufferToByteString() {
    val actual = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)).toByteString()
    val expected = ByteString.of(1, 2, 3, 4)
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun stringEncodeByteStringDefaultCharset() {
    val actual = "a\uD83C\uDF69c".encode()
    val expected = "a\uD83C\uDF69c".encodeUtf8()
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun streamReadByteString() {
    val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    val actual = stream.readByteString(4)
    val expected = ByteString.of(1, 2, 3, 4)
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun substring() {
    val byteString = "abcdef".encodeUtf8()
    assertThat(byteString.substring()).isEqualTo("abcdef".encodeUtf8())
    assertThat(byteString.substring(endIndex = 3)).isEqualTo("abc".encodeUtf8())
    assertThat(byteString.substring(beginIndex = 3)).isEqualTo("def".encodeUtf8())
    assertThat(byteString.substring(beginIndex = 1, endIndex = 5)).isEqualTo("bcde".encodeUtf8())
  }
}