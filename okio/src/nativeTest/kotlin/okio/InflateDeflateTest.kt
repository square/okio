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
import kotlin.test.assertTrue
import okio.ByteString.Companion.toByteString
import platform.zlib.Z_BEST_COMPRESSION

class InflateDeflateTest {
  /** The compressed data is 0.1% of the size of the original. */
  @Test
  fun deflateInflate_compressionRatio0_01() {
    deflateInflate(
      contentList = Array(16) {
        ByteArray(1024 * 64) { 0 }.toByteString()
      },
      goldenCompressedSize = 1_330,
    )
  }

  /** The compressed data is 100% of the size of the original. */
  @Test
  fun deflateInflate_compressionRatio100_0() {
    deflateInflate(
      contentList = Array(16) {
        randomBytes(1024 * 64, seed = it)
      },
      goldenCompressedSize = 1_048_978,
    )
  }

  /** The compressed data is 700% of the size of the original. */
  @Test
  fun deflateInflate_compressionRatio700_0() {
    deflateInflate(
      contentList = Array(1024 * 64) {
        randomBytes(1, seed = it)
      },
      goldenCompressedSize = 458_959,
    )
  }

  @Test
  fun deflateInflateEmpty() {
    deflateInflate(
      contentList = arrayOf(),
      goldenCompressedSize = 2,
    )
  }

  private fun deflateInflate(
    contentList: Array<ByteString>,
    goldenCompressedSize: Long,
  ) {
    val data = Buffer()

    val deflaterSink = DeflaterSink(
      sink = data,
      deflater = Deflater(level = Z_BEST_COMPRESSION, nowrap = true),
    )
    deflaterSink.buffer().use {
      for (c in contentList) {
        it.write(c)
        it.flush()
      }
    }

    assertEquals(goldenCompressedSize, data.size)

    val inflaterSource = InflaterSource(
      source = data,
      inflater = Inflater(nowrap = true),
    )
    inflaterSource.buffer().use {
      for (content in contentList) {
        assertEquals(content, it.readByteString(content.size.toLong()))
      }
      assertTrue(it.exhausted())
    }
  }
}
