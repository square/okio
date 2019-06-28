/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package okio

import okio.ByteString.Companion.decodeBase64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class InflaterSourceTest {
  @Test fun inflate() {
    val deflated = decodeBase64(
      "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s="
    )
    val inflated = inflate(deflated)
    assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8())
  }

  @Test fun inflateTruncated() {
    val deflated = decodeBase64(
      "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CDw=="
    )
    try {
      inflate(deflated)
      fail()
    } catch (expected: EOFException) {
    }
  }

  @Test fun inflateWellCompressed() {
    val deflated = decodeBase64(
      "eJztwTEBAAAAwqCs61/CEL5AAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB8B" +
        "tFeWvE=\n"
    )
    val original = 'a'.repeat(1024 * 1024)
    val inflated = inflate(deflated)
    assertEquals(original, inflated.readUtf8())
  }

  @Test fun inflatePoorlyCompressed() {
    val original = randomBytes(1024 * 1024)
    val deflated = deflate(original)
    val inflated = inflate(deflated)
    assertEquals(original, inflated.readByteString())
  }

  @Test fun inflateIntoNonemptySink() {
    for (i in 0 until Segment.SIZE) {
      val inflated = Buffer().writeUtf8('a'.repeat(i))
      val deflated = decodeBase64(
        "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s="
      )
      val source = InflaterSource(deflated, Inflater())
      source.readAll(inflated)
      inflated.skip(i.toLong())
      assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8())
    }
  }

  @Test fun inflateSingleByte() {
    val inflated = Buffer()
    val deflated = decodeBase64(
      "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s="
    )
    val source = InflaterSource(deflated, Inflater())
    source.read(inflated, 1)
    source.close()
    assertEquals("G", inflated.readUtf8())
    assertEquals(0, inflated.size)
  }

  @Test fun inflateByteCount() {
    val inflated = Buffer()
    val deflated = decodeBase64(
      "eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s="
    )
    val source = InflaterSource(deflated, Inflater())
    source.read(inflated, 11)
    source.close()
    assertEquals("God help us", inflated.readUtf8())
    assertEquals(0, inflated.size)
  }

  private fun decodeBase64(s: String): Buffer {
    return Buffer().write(s.decodeBase64()!!)
  }

  /** Use DeflaterOutputStream to deflate source.  */
  private fun deflate(source: ByteString): Buffer {
    val result = Buffer()
    val sink = DeflaterSink(result)
    sink.write(Buffer().write(source), source.size.toLong())
    sink.close()
    return result
  }

  /** Returns a new buffer containing the inflated contents of `deflated`.  */
  private fun inflate(deflated: Buffer): Buffer {
    val result = Buffer()
    val source = InflaterSource(deflated)
    source.readAll(result)
    return result
  }

  private fun Source.readAll(sink: Buffer) {
    while (read(sink, Int.MAX_VALUE.toLong()) != -1L) {
    }
  }
}
