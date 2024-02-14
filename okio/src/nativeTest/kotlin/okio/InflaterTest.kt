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
    val inflater = Inflater(nowrap = true)
    inflater.dataProcessor.apply {
      source = "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA="
        .decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      assertTrue(process())
      assertTrue(finished)
      assertEquals(sourceLimit, sourcePos)

      val inflated = target.toByteString(0, targetPos)
      assertEquals(
        "God help us, we're in the hands of engineers.",
        inflated.utf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateInParts() {
    val inflater = Inflater(nowrap = true)
    inflater.dataProcessor.apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      source = "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJ".decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      assertTrue(process())
      assertFalse(finished)
      assertEquals(sourceLimit, sourcePos)

      source = "SFXISMxLKVbIT1NIzUvPzEtNLSrWAwA=".decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      assertTrue(process())
      assertTrue(finished)
      assertEquals(sourceLimit, sourcePos)

      val inflated = target.toByteString(0, targetPos)
      assertEquals(
        "God help us, we're in the hands of engineers.",
        inflated.utf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateInsufficientSpaceInTarget() {
    val targetBuffer = Buffer()

    val inflater = Inflater(nowrap = true)
    inflater.dataProcessor.apply {
      source = "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA="
        .decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size

      target = ByteArray(31)
      targetPos = 0
      targetLimit = target.size
      assertFalse(process())
      assertFalse(finished)
      assertEquals(targetLimit, targetPos)
      targetBuffer.write(target)

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
      assertTrue(process())
      assertTrue(finished)
      assertEquals(sourcePos, sourceLimit)
      targetBuffer.write(target, 0, targetPos)

      assertEquals(
        "God help us, we're in the hands of engineers.",
        targetBuffer.readUtf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateEmptyContent() {
    val inflater = Inflater(nowrap = true)
    inflater.dataProcessor.apply {
      source = "AwA=".decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      assertTrue(process())
      assertTrue(finished)

      val inflated = target.toByteString(0, targetPos)
      assertEquals(
        "",
        inflated.utf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateInPartsStartingWithEmptySource() {
    val inflater = Inflater(nowrap = true)
    val dataProcessor = inflater.dataProcessor
    dataProcessor.apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      source = ByteArray(256)
      sourcePos = 0
      sourceLimit = 0
      assertTrue(process())
      assertFalse(finished)

      source = "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA="
        .decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      assertTrue(process())
      assertTrue(finished)

      val inflated = target.toByteString(0, targetPos)
      assertEquals(
        "God help us, we're in the hands of engineers.",
        inflated.utf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateInvalidData() {
    val inflater = Inflater(nowrap = true)
    val dataProcessor = inflater.dataProcessor
    dataProcessor.apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      source = "ffffffffffffffff".decodeHex().toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      val exception = assertFailsWith<ProtocolException> {
        process()
      }
      assertFalse(finished)
      assertEquals("Z_DATA_ERROR", exception.message)

      inflater.end()
    }
  }

  @Test
  fun cannotInflateAfterEnd() {
    val inflater = Inflater(nowrap = true)
    inflater.end()

    assertFailsWith<IllegalStateException> {
      inflater.dataProcessor.process()
    }
  }

  @Test
  fun endIsIdemptent() {
    val inflater = Inflater(nowrap = true)
    inflater.end()
    inflater.end()
  }
}
