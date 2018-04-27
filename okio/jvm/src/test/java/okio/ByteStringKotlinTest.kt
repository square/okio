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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ByteStringKotlinTest {
  @Test fun decodeBase64() {
    val actual = "YfCfjalj".decodeBase64()
    val expected = ByteString.encodeUtf8("a\uD83C\uDF69c")
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
}