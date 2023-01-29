/*
 * Copyright (C) 2020 Square, Inc.
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
package okio.internal

import okio.leftRotate

internal class Md5 : HashFunction {
  private var messageLength = 0L
  private val unprocessed = ByteArray(64)
  private var unprocessedLimit = 0
  private val words = IntArray(16)

  private var h0: Int = 1732584193
  private var h1: Int = -271733879
  private var h2: Int = -1732584194
  private var h3: Int = 271733878

  override fun update(
    input: ByteArray,
    offset: Int,
    byteCount: Int,
  ) {
    messageLength += byteCount
    var pos = offset
    val limit = pos + byteCount
    val unprocessed = this.unprocessed
    val unprocessedLimit = this.unprocessedLimit

    if (unprocessedLimit > 0) {
      if (unprocessedLimit + byteCount < 64) {
        // Not enough bytes for a chunk.
        input.copyInto(unprocessed, unprocessedLimit, pos, limit)
        this.unprocessedLimit = unprocessedLimit + byteCount
        return
      }

      // Process a chunk combining leftover bytes and the input.
      val consumeByteCount = 64 - unprocessedLimit
      input.copyInto(unprocessed, unprocessedLimit, pos, pos + consumeByteCount)
      processChunk(unprocessed, 0)
      this.unprocessedLimit = 0
      pos += consumeByteCount
    }

    while (pos < limit) {
      val nextPos = pos + 64

      if (nextPos > limit) {
        // Not enough bytes for a chunk.
        input.copyInto(unprocessed, 0, pos, limit)
        this.unprocessedLimit = limit - pos
        return
      }

      // Process a chunk.
      processChunk(input, pos)
      pos = nextPos
    }
  }

  private fun processChunk(input: ByteArray, pos: Int) {
    val words = this.words

    var pos = pos
    for (w in 0 until 16) {
      words[w] = ((input[pos++].toInt() and 0xff)) or
        ((input[pos++].toInt() and 0xff) shl 8) or
        ((input[pos++].toInt() and 0xff) shl 16) or
        ((input[pos++].toInt() and 0xff) shl 24)
    }

    hash(words)
  }

  private fun hash(words: IntArray) {
    val localK = k
    val localS = s

    var a = h0
    var b = h1
    var c = h2
    var d = h3

    for (i in 0 until 16) {
      val g = i
      val f = ((b and c) or (b.inv() and d)) + a + localK[i] + words[g]
      a = d
      d = c
      c = b
      b += f leftRotate localS[i]
    }

    for (i in 16 until 32) {
      val g = ((5 * i) + 1) % 16
      val f = ((d and b) or (d.inv() and c)) + a + localK[i] + words[g]
      a = d
      d = c
      c = b
      b += f leftRotate localS[i]
    }

    for (i in 32 until 48) {
      val g = ((3 * i) + 5) % 16
      val f = (b xor c xor d) + a + localK[i] + words[g]
      a = d
      d = c
      c = b
      b += f leftRotate localS[i]
    }

    for (i in 48 until 64) {
      val g = (7 * i) % 16
      val f = (c xor (b or d.inv())) + a + localK[i] + words[g]
      a = d
      d = c
      c = b
      b += f leftRotate localS[i]
    }

    h0 += a
    h1 += b
    h2 += c
    h3 += d
  }

  /* ktlint-disable */
  override fun digest(): ByteArray {
    val messageLengthBits = messageLength * 8

    unprocessed[unprocessedLimit++] = 0x80.toByte()
    if (unprocessedLimit > 56) {
      unprocessed.fill(0, unprocessedLimit, 64)
      processChunk(unprocessed, 0)
      unprocessed.fill(0, 0, unprocessedLimit)
    } else {
      unprocessed.fill(0, unprocessedLimit, 56)
    }
    unprocessed[56] = (messageLengthBits        ).toByte()
    unprocessed[57] = (messageLengthBits ushr  8).toByte()
    unprocessed[58] = (messageLengthBits ushr 16).toByte()
    unprocessed[59] = (messageLengthBits ushr 24).toByte()
    unprocessed[60] = (messageLengthBits ushr 32).toByte()
    unprocessed[61] = (messageLengthBits ushr 40).toByte()
    unprocessed[62] = (messageLengthBits ushr 48).toByte()
    unprocessed[63] = (messageLengthBits ushr 56).toByte()
    processChunk(unprocessed, 0)

    val a = h0
    val b = h1
    val c = h2
    val d = h3

    return byteArrayOf(
      (a       ).toByte(),
      (a shr  8).toByte(),
      (a shr 16).toByte(),
      (a shr 24).toByte(),
      (b       ).toByte(),
      (b shr  8).toByte(),
      (b shr 16).toByte(),
      (b shr 24).toByte(),
      (c       ).toByte(),
      (c shr  8).toByte(),
      (c shr 16).toByte(),
      (c shr 24).toByte(),
      (d       ).toByte(),
      (d shr  8).toByte(),
      (d shr 16).toByte(),
      (d shr 24).toByte()
    )
  }
  /* ktlint-enable */

  companion object {
    private val s = intArrayOf(
      7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9,
      14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15,
      21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    private val k = intArrayOf(
      -680876936, -389564586, 606105819, -1044525330, -176418897, 1200080426, -1473231341,
      -45705983, 1770035416, -1958414417, -42063, -1990404162, 1804603682, -40341101, -1502002290,
      1236535329, -165796510, -1069501632, 643717713, -373897302, -701558691, 38016083, -660478335,
      -405537848, 568446438, -1019803690, -187363961, 1163531501, -1444681467, -51403784,
      1735328473, -1926607734, -378558, -2022574463, 1839030562, -35309556, -1530992060, 1272893353,
      -155497632, -1094730640, 681279174, -358537222, -722521979, 76029189, -640364487, -421815835,
      530742520, -995338651, -198630844, 1126891415, -1416354905, -57434055, 1700485571,
      -1894986606, -1051523, -2054922799, 1873313359, -30611744, -1560198380, 1309151649,
      -145523070, -1120210379, 718787259, -343485551,
    )
  }
}
