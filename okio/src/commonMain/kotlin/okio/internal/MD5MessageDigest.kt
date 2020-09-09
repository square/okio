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

private val s = intArrayOf(
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
  5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
  4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
  6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
)

@ExperimentalUnsignedTypes
private val k = intArrayOf(
  0xd76aa478u.toInt(), 0xe8c7b756u.toInt(), 0x242070dbu.toInt(), 0xc1bdceeeu.toInt(),
  0xf57c0fafu.toInt(), 0x4787c62au.toInt(), 0xa8304613u.toInt(), 0xfd469501u.toInt(),
  0x698098d8u.toInt(), 0x8b44f7afu.toInt(), 0xffff5bb1u.toInt(), 0x895cd7beu.toInt(),
  0x6b901122u.toInt(), 0xfd987193u.toInt(), 0xa679438eu.toInt(), 0x49b40821u.toInt(),
  0xf61e2562u.toInt(), 0xc040b340u.toInt(), 0x265e5a51u.toInt(), 0xe9b6c7aau.toInt(),
  0xd62f105du.toInt(), 0x02441453u.toInt(), 0xd8a1e681u.toInt(), 0xe7d3fbc8u.toInt(),
  0x21e1cde6u.toInt(), 0xc33707d6u.toInt(), 0xf4d50d87u.toInt(), 0x455a14edu.toInt(),
  0xa9e3e905u.toInt(), 0xfcefa3f8u.toInt(), 0x676f02d9u.toInt(), 0x8d2a4c8au.toInt(),
  0xfffa3942u.toInt(), 0x8771f681u.toInt(), 0x6d9d6122u.toInt(), 0xfde5380cu.toInt(),
  0xa4beea44u.toInt(), 0x4bdecfa9u.toInt(), 0xf6bb4b60u.toInt(), 0xbebfbc70u.toInt(),
  0x289b7ec6u.toInt(), 0xeaa127fau.toInt(), 0xd4ef3085u.toInt(), 0x04881d05u.toInt(),
  0xd9d4d039u.toInt(), 0xe6db99e5u.toInt(), 0x1fa27cf8u.toInt(), 0xc4ac5665u.toInt(),
  0xf4292244u.toInt(), 0x432aff97u.toInt(), 0xab9423a7u.toInt(), 0xfc93a039u.toInt(),
  0x655b59c3u.toInt(), 0x8f0ccc92u.toInt(), 0xffeff47du.toInt(), 0x85845dd1u.toInt(),
  0x6fa87e4fu.toInt(), 0xfe2ce6e0u.toInt(), 0xa3014314u.toInt(), 0x4e0811a1u.toInt(),
  0xf7537e82u.toInt(), 0xbd3af235u.toInt(), 0x2ad7d2bbu.toInt(), 0xeb86d391u.toInt()
)

@ExperimentalUnsignedTypes
internal class MD5MessageDigest : OkioMessageDigest {
  private var messageLength = 0L
  private val unprocessed = ByteArray(64)
  private var unprocessedLimit = 0
  private val words = IntArray(16)

  private var h0: Int = 0x67452301u.toInt()
  private var h1: Int = 0xefcdab89u.toInt()
  private var h2: Int = 0x98badcfeu.toInt()
  private var h3: Int = 0x10325476u.toInt()

  override fun update(
    input: ByteArray,
    offset: Int,
    byteCount: Int
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
}
