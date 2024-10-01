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
    val expected = "God help us, we're in the hands of engineers."
    val inflater = Inflater()
    inflater.dataProcessor.apply {
      source = "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s="
        .decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      assertTrue(process())
      assertTrue(finished)
      assertEquals(sourceLimit, sourcePos)
      assertEquals(expected.length.toLong(), inflater.getBytesWritten())

      val inflated = target.toByteString(0, targetPos)
      assertEquals(
        expected,
        inflated.utf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun happyPathNoWrap() {
    val content = "God help us, we're in the hands of engineers."
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
      assertEquals(content.length.toLong(), inflater.getBytesWritten())

      val inflated = target.toByteString(0, targetPos)
      assertEquals(
        content,
        inflated.utf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateInParts() {
    val content = "God help us, we're in the hands of engineers."
    val inflater = Inflater()
    inflater.dataProcessor.apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      source = "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxT".decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      assertTrue(process())
      assertFalse(finished)
      assertEquals(sourceLimit, sourcePos)
      assertEquals(21, inflater.getBytesWritten())

      source = "KMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=".decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      assertTrue(process())
      assertTrue(finished)
      assertEquals(sourceLimit, sourcePos)
      assertEquals(content.length.toLong(), inflater.getBytesWritten())

      val inflated = target.toByteString(0, targetPos)
      assertEquals(
        content,
        inflated.utf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateInsufficientSpaceInTarget() {
    val targetBuffer = Buffer()

    val content = "God help us, we're in the hands of engineers."
    val inflater = Inflater()
    inflater.dataProcessor.apply {
      source = "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s="
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
      assertEquals(31, inflater.getBytesWritten())

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
      assertTrue(process())
      assertTrue(finished)
      assertEquals(sourcePos, sourceLimit)
      targetBuffer.write(target, 0, targetPos)
      assertEquals(content.length.toLong(), inflater.getBytesWritten())

      assertEquals(
        content,
        targetBuffer.readUtf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateEmptyContent() {
    val inflater = Inflater()
    inflater.dataProcessor.apply {
      source = "eJwDAAAAAAE=".decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      assertTrue(process())
      assertTrue(finished)
      assertEquals(0L, inflater.getBytesWritten())

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
    val content = "God help us, we're in the hands of engineers."
    val inflater = Inflater()
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
      assertEquals(0, inflater.getBytesWritten())

      source = "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s="
        .decodeBase64()!!.toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      assertTrue(process())
      assertTrue(finished)
      assertEquals(content.length.toLong(), inflater.getBytesWritten())

      val inflated = target.toByteString(0, targetPos)
      assertEquals(
        content,
        inflated.utf8(),
      )

      inflater.end()
    }
  }

  @Test
  fun inflateInvalidData() {
    val inflater = Inflater()
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
      assertEquals(0L, inflater.getBytesWritten())

      inflater.end()
    }
  }

  @Test
  fun cannotInflateAfterEnd() {
    val inflater = Inflater()
    inflater.end()

    assertFailsWith<IllegalStateException> {
      inflater.dataProcessor.process()
    }
  }

  @Test
  fun cannotGetBytesWrittenAfterEnd() {
    val inflater = Inflater()
    inflater.end()

    assertFailsWith<IllegalStateException> {
      inflater.getBytesWritten()
    }
  }

  @Test
  fun endIsIdemptent() {
    val inflater = Inflater()
    inflater.end()
    inflater.end()
  }
}
