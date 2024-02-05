/*
 * Copyright (C) 2024 Square, Inc.
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

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class DeflaterTest {
  @Test
  fun happyPath() {
    val deflater = Deflater().apply {
      source = "God help us, we're in the hands of engineers.".encodeUtf8().toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      sourceFinished = true

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
    }

    assertThat(deflater.deflate()).isTrue()
    assertThat(deflater.sourcePos).isEqualTo(deflater.sourceLimit)
    val deflated = deflater.target.toByteString(0, deflater.targetPos)

    // Golden compressed output.
    assertThat(deflated)
      .isEqualTo("c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA=".decodeBase64())

    deflater.close()
  }

  @Test
  fun deflateInParts() {
    val deflater = Deflater().apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
    }

    deflater.source = "God help us, we're in the hands".encodeUtf8().toByteArray()
    deflater.sourcePos = 0
    deflater.sourceLimit = deflater.source.size
    deflater.sourceFinished = false
    assertThat(deflater.deflate()).isTrue()
    assertThat(deflater.sourcePos).isEqualTo(deflater.sourceLimit)

    deflater.source = " of engineers.".encodeUtf8().toByteArray()
    deflater.sourcePos = 0
    deflater.sourceLimit = deflater.source.size
    deflater.sourceFinished = true
    assertThat(deflater.deflate()).isTrue()
    assertThat(deflater.sourcePos).isEqualTo(deflater.sourceLimit)

    val deflated = deflater.target.toByteString(0, deflater.targetPos)

    // Golden compressed output.
    assertThat(deflated)
      .isEqualTo("c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA=".decodeBase64())

    deflater.close()
  }

  @Test
  fun deflateEmptySource() {
    val deflater = Deflater().apply {
      sourceFinished = true

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
    }

    assertThat(deflater.deflate()).isTrue()
    val deflated = deflater.target.toByteString(0, deflater.targetPos)

    // Golden compressed output.
    assertThat(deflated)
      .isEqualTo("AwA=".decodeBase64())

    deflater.close()
  }
}
