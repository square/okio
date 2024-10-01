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
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import platform.zlib.Z_BEST_COMPRESSION
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_SYNC_FLUSH

class DeflaterTest {
  @Test
  fun happyPath() {
    val content = "God help us, we're in the hands of engineers."
    val deflater = Deflater()
    deflater.dataProcessor.apply {
      source = content.encodeUtf8().toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      deflater.flush = Z_FINISH

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      assertTrue(process())
      assertEquals(sourceLimit, sourcePos)
      assertEquals(content.length.toLong(), deflater.getBytesRead())
      val deflated = target.toByteString(0, targetPos)

      // Golden compressed output.
      assertEquals(
        "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=".decodeBase64(),
        deflated,
      )

      deflater.end()
    }
  }

  @Test
  fun happyPathNoWrap() {
    val content = "God help us, we're in the hands of engineers."
    val deflater = Deflater(nowrap = true)
    deflater.dataProcessor.apply {
      source = content.encodeUtf8().toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      deflater.flush = Z_FINISH

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      assertTrue(process())
      assertEquals(sourceLimit, sourcePos)
      assertEquals(content.length.toLong(), deflater.getBytesRead())
      val deflated = target.toByteString(0, targetPos)

      // Golden compressed output.
      assertEquals(
        "c89PUchIzSlQKC3WUShPVS9KVcjMUyjJSFXISMxLKVbIT1NIzUvPzEtNLSrWAwA=".decodeBase64(),
        deflated,
      )

      deflater.end()
    }
  }

  @Test
  fun deflateInParts() {
    val contentA = "God help us, we're in the hands"
    val contentB = " of engineers."
    val deflater = Deflater()
    deflater.dataProcessor.apply {
      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      source = contentA.encodeUtf8().toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      assertTrue(process())
      assertEquals(sourceLimit, sourcePos)
      assertEquals(contentA.length.toLong(), deflater.getBytesRead())

      source = contentB.encodeUtf8().toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      deflater.flush = Z_FINISH
      assertTrue(process())
      assertEquals(sourceLimit, sourcePos)
      assertEquals((contentA + contentB).length.toLong(), deflater.getBytesRead())

      val deflated = target.toByteString(0, targetPos)

      // Golden compressed output.
      assertEquals(
        "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=".decodeBase64(),
        deflated,
      )

      deflater.end()
    }
  }

  @Test
  fun deflateInsufficientSpaceInTargetWithoutSourceFinished() {
    val targetBuffer = Buffer()

    val content = "God help us, we're in the hands of engineers."
    val deflater = Deflater()
    deflater.dataProcessor.apply {
      source = content.encodeUtf8().toByteArray()
      sourcePos = 0
      sourceLimit = source.size

      target = ByteArray(10)
      targetPos = 0
      targetLimit = target.size
      deflater.flush = Z_SYNC_FLUSH
      assertFalse(process())
      assertEquals(targetLimit, targetPos)
      assertEquals(content.length.toLong(), deflater.getBytesRead())
      targetBuffer.write(target)

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
      deflater.flush = Z_NO_FLUSH
      assertTrue(process())
      assertEquals(sourcePos, sourceLimit)
      assertEquals(content.length.toLong(), deflater.getBytesRead())
      targetBuffer.write(target, 0, targetPos)

      deflater.flush = Z_FINISH
      assertTrue(process())

      // Golden compressed output.
      assertEquals(
        "eJxyz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYD".decodeBase64(),
        targetBuffer.readByteString(),
      )

      deflater.end()
    }
  }

  @Test
  fun deflateInsufficientSpaceInTargetWithSourceFinished() {
    val targetBuffer = Buffer()

    val content = "God help us, we're in the hands of engineers."
    val deflater = Deflater()
    deflater.dataProcessor.apply {
      source = content.encodeUtf8().toByteArray()
      sourcePos = 0
      sourceLimit = source.size
      deflater.flush = Z_FINISH

      target = ByteArray(10)
      targetPos = 0
      targetLimit = target.size
      assertFalse(process())
      assertEquals(targetLimit, targetPos)
      assertEquals(content.length.toLong(), deflater.getBytesRead())
      targetBuffer.write(target)

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size
      assertTrue(process())
      assertEquals(sourcePos, sourceLimit)
      assertEquals(content.length.toLong(), deflater.getBytesRead())
      targetBuffer.write(target, 0, targetPos)

      // Golden compressed output.
      assertEquals(
        "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=".decodeBase64(),
        targetBuffer.readByteString(),
      )

      deflater.end()
    }
  }

  @Test
  fun deflateEmptySource() {
    val deflater = Deflater()
    deflater.dataProcessor.apply {
      deflater.flush = Z_FINISH

      target = ByteArray(256)
      targetPos = 0
      targetLimit = target.size

      assertTrue(process())
      assertEquals(0L, deflater.getBytesRead())
      val deflated = target.toByteString(0, targetPos)

      // Golden compressed output.
      assertEquals(
        "eJwDAAAAAAE=".decodeBase64(),
        deflated,
      )

      deflater.end()
    }
  }

  @Test
  fun cannotDeflateAfterEnd() {
    val deflater = Deflater()
    deflater.end()

    assertFailsWith<IllegalStateException> {
      deflater.dataProcessor.process()
    }
  }

  @Test
  fun cannotGetBytesReadAfterEnd() {
    val deflater = Deflater()
    deflater.end()

    assertFailsWith<IllegalStateException> {
      deflater.getBytesRead()
    }
  }

  @Test
  fun endIsIdemptent() {
    val deflater = Deflater()
    deflater.end()
    deflater.end()
  }

  private fun Deflater(nowrap: Boolean) = Deflater(Z_BEST_COMPRESSION, nowrap)
}
