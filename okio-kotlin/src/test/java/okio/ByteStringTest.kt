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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import kotlin.text.Charsets.UTF_16BE

class ByteStringTest {
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
    val actual = "a\uD83C\uDF69c".encodeByteString()
    val expected = ByteString.encodeUtf8("a\uD83C\uDF69c")
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun stringEncodeByteStringWithCharset() {
    val actual = "a\uD83C\uDF69c".encodeByteString(UTF_16BE)
    val expected = ByteString.encodeString("a\uD83C\uDF69c", UTF_16BE)
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun decodeBase64() {
    val actual = "YfCfjalj".decodeBase64()
    val expected = ByteString.decodeBase64("YfCfjalj")
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun decodeBase64Invalid() {
    val actual = ";-)".decodeBase64()
    assertThat(actual).isNull()
  }

  @Test fun decodeHex() {
    val actual = "CAFEBABE".decodeHex()
    val expected = ByteString.decodeHex("CAFEBABE")
    assertThat(actual).isEqualTo(expected)
  }

  @Test fun streamReadByteString() {
    val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
    val actual = stream.readByteString(4)
    val expected = ByteString.of(1, 2, 3, 4)
    assertThat(actual).isEqualTo(expected)
  }
}
