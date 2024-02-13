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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

class InflaterTest {
  @Test
  fun happyPath() {
    val inflater = Inflater().apply {
      source = "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA="
        .decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
    }

    assertTrue(inflater.process())
    assertTrue(inflater.finished)
    assertEquals(inflater.sourceLimit, inflater.sourcePos)

    val inflated = inflater.target.toByteString(0, inflater.targetPos)
    assertEquals(
      "God help us, we're in the hands of engineers.",
      inflated.utf8(),
    )

    inflater.close()
  }

  @Test
  fun inflateInParts() {
    val inflater = Inflater().apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
    }

    inflater.source = "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJ".decodeBase64()!!.toByteArray()
    inflater.sourcePos = 0
    inflater.sourceLimit = inflater.source.size
    assertTrue(inflater.process())
    assertFalse(inflater.finished)
    assertEquals(inflater.sourceLimit, inflater.sourcePos)

    inflater.source = "SFXISMxLKVbIT1NIzUvPzEtNLSrWAwA=".decodeBase64()!!.toByteArray()
    inflater.sourcePos = 0
    inflater.sourceLimit = inflater.source.size
    assertTrue(inflater.process())
    assertTrue(inflater.finished)
    assertEquals(inflater.sourceLimit, inflater.sourcePos)

    val inflated = inflater.target.toByteString(0, inflater.targetPos)
    assertEquals(
      "God help us, we're in the hands of engineers.",
      inflated.utf8(),
    )

    inflater.close()
  }

  @Test
  fun inflateInsufficientSpaceInTarget() {
    val targetBuffer = Buffer()

    val inflater = Inflater().apply {
      source = "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA="
        .decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size
    }

    inflater.target = ByteArray(31)
    inflater.targetPos = 0
    inflater.targetLimit = inflater.target.size
    assertFalse(inflater.process())
    assertFalse(inflater.finished)
    assertEquals(inflater.targetLimit, inflater.targetPos)
    targetBuffer.write(inflater.target)

    inflater.target = ByteArray(256)
    inflater.targetPos = 0
    inflater.targetLimit = inflater.target.size
    assertTrue(inflater.process())
    assertTrue(inflater.finished)
    assertEquals(inflater.sourcePos, inflater.sourceLimit)
    targetBuffer.write(inflater.target, 0, inflater.targetPos)

    assertEquals(
      "God help us, we're in the hands of engineers.",
      targetBuffer.readUtf8(),
    )

    inflater.close()
  }

  @Test
  fun inflateEmptyContent() {
    val inflater = Inflater().apply {
      source = "AwA=".decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
    }

    assertTrue(inflater.process())
    assertTrue(inflater.finished)

    val inflated = inflater.target.toByteString(0, inflater.targetPos)
    assertEquals(
      "",
      inflated.utf8(),
    )

    inflater.close()
  }

  @Test
  fun inflateInPartsStartingWithEmptySource() {
    val inflater = Inflater().apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
    }

    inflater.source = ByteArray(256)
    inflater.sourcePos = 0
    inflater.sourceLimit = 0
    assertTrue(inflater.process())
    assertFalse(inflater.finished)

    inflater.source = "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA="
      .decodeBase64()!!.toByteArray()
    inflater.sourcePos = 0
    inflater.sourceLimit = inflater.source.size
    assertTrue(inflater.process())
    assertTrue(inflater.finished)

    val inflated = inflater.target.toByteString(0, inflater.targetPos)
    assertEquals(
      "God help us, we're in the hands of engineers.",
      inflated.utf8(),
    )

    inflater.close()
  }

  @Test
  fun inflateInvalidData() {
    val inflater = Inflater().apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
    }

    inflater.source = "ffffffffffffffff".decodeHex().toByteArray()
    inflater.sourcePos = 0
    inflater.sourceLimit = inflater.source.size
    val exception = assertFailsWith<ProtocolException> {
      inflater.process()
    }
    assertFalse(inflater.finished)
    assertEquals("Z_DATA_ERROR", exception.message)

    inflater.close()
  }

  @Test
  fun cannotInflateAfterClose() {
    val inflater = Inflater()
    inflater.close()

    assertFailsWith<IllegalStateException> {
      inflater.process()
    }
  }

  @Test
  fun closeIsIdemptent() {
    val inflater = Inflater()
    inflater.close()
    inflater.close()
  }
}
