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

import okio.and

@ExperimentalUnsignedTypes
private val k = intArrayOf(
  0x428a2f98u.toInt(), 0x71374491u.toInt(), 0xb5c0fbcfu.toInt(), 0xe9b5dba5u.toInt(),
  0x3956c25bu.toInt(), 0x59f111f1u.toInt(), 0x923f82a4u.toInt(), 0xab1c5ed5u.toInt(),
  0xd807aa98u.toInt(), 0x12835b01u.toInt(), 0x243185beu.toInt(), 0x550c7dc3u.toInt(),
  0x72be5d74u.toInt(), 0x80deb1feu.toInt(), 0x9bdc06a7u.toInt(), 0xc19bf174u.toInt(),
  0xe49b69c1u.toInt(), 0xefbe4786u.toInt(), 0x0fc19dc6u.toInt(), 0x240ca1ccu.toInt(),
  0x2de92c6fu.toInt(), 0x4a7484aau.toInt(), 0x5cb0a9dcu.toInt(), 0x76f988dau.toInt(),
  0x983e5152u.toInt(), 0xa831c66du.toInt(), 0xb00327c8u.toInt(), 0xbf597fc7u.toInt(),
  0xc6e00bf3u.toInt(), 0xd5a79147u.toInt(), 0x06ca6351u.toInt(), 0x14292967u.toInt(),
  0x27b70a85u.toInt(), 0x2e1b2138u.toInt(), 0x4d2c6dfcu.toInt(), 0x53380d13u.toInt(),
  0x650a7354u.toInt(), 0x766a0abbu.toInt(), 0x81c2c92eu.toInt(), 0x92722c85u.toInt(),
  0xa2bfe8a1u.toInt(), 0xa81a664bu.toInt(), 0xc24b8b70u.toInt(), 0xc76c51a3u.toInt(),
  0xd192e819u.toInt(), 0xd6990624u.toInt(), 0xf40e3585u.toInt(), 0x106aa070u.toInt(),
  0x19a4c116u.toInt(), 0x1e376c08u.toInt(), 0x2748774cu.toInt(), 0x34b0bcb5u.toInt(),
  0x391c0cb3u.toInt(), 0x4ed8aa4au.toInt(), 0x5b9cca4fu.toInt(), 0x682e6ff3u.toInt(),
  0x748f82eeu.toInt(), 0x78a5636fu.toInt(), 0x84c87814u.toInt(), 0x8cc70208u.toInt(),
  0x90befffau.toInt(), 0xa4506cebu.toInt(), 0xbef9a3f7u.toInt(), 0xc67178f2u.toInt()
)

@ExperimentalUnsignedTypes
internal class Sha256MessageDigest : OkioMessageDigest {
  private var messageLength = 0L
  private val unprocessed = ByteArray(64)
  private var unprocessedLimit = 0
  private val words = IntArray(64)

  private var h0 = 0x6a09e667u.toInt()
  private var h1 = 0xbb67ae85u.toInt()
  private var h2 = 0x3c6ef372u.toInt()
  private var h3 = 0xa54ff53au.toInt()
  private var h4 = 0x510e527fu.toInt()
  private var h5 = 0x9b05688cu.toInt()
  private var h6 = 0x1f83d9abu.toInt()
  private var h7 = 0x5be0cd19u.toInt()

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
      words[w] = ((input[pos++] and 0xff) shl 24) or
        ((input[pos++] and 0xff) shl 16) or
        ((input[pos++] and 0xff) shl 8) or
        ((input[pos++] and 0xff))
    }

    for (w in 16 until 64) {
      val w15 = words[w - 15]
      val s0 = ((w15 ushr 7) or (w15 shl 25)) xor ((w15 ushr 18) or (w15 shl 14)) xor (w15 ushr 3)
      val w2 = words[w - 2]
      val s1 = ((w2 ushr 17) or (w2 shl 15)) xor ((w2 ushr 19) or (w2 shl 13)) xor (w2 ushr 10)
      val w16 = words[w - 16]
      val w7 = words[w - 7]
      words[w] = w16 + s0 + w7 + s1
    }

    hash(words)
  }

  private fun hash(
    words: IntArray
  ) {
    val localK = k
    var a = h0
    var b = h1
    var c = h2
    var d = h3
    var e = h4
    var f = h5
    var g = h6
    var h = h7

    for (i in 0 until 64) {
      val s0 = ((a ushr 2) or (a shl 30)) xor
        ((a ushr 13) or (a shl 19)) xor
        ((a ushr 22) or (a shl 10))
      val s1 = ((e ushr 6) or (e shl 26)) xor
        ((e ushr 11) or (e shl 21)) xor
        ((e ushr 25) or (e shl 7))

      val ch = (e and f) xor
        (e.inv() and g)
      val maj = (a and b) xor
        (a and c) xor
        (b and c)

      val t1 = h + s1 + ch + localK[i] + words[i]
      val t2 = s0 + maj

      h = g
      g = f
      f = e
      e = d + t1
      d = c
      c = b
      b = a
      a = t1 + t2
    }

    h0 += a
    h1 += b
    h2 += c
    h3 += d
    h4 += e
    h5 += f
    h6 += g
    h7 += h
  }

  /* ktlint-disable */
  override fun digest(): ByteArray {
    val unprocessed = this.unprocessed
    var unprocessedLimit = this.unprocessedLimit
    val messageLengthBits = messageLength * 8

    unprocessed[unprocessedLimit++] = 0x80.toByte()
    if (unprocessedLimit > 56) {
      unprocessed.fill(0, unprocessedLimit, 64)
      processChunk(unprocessed, 0)
      unprocessed.fill(0, 0, unprocessedLimit)
    } else {
      unprocessed.fill(0, unprocessedLimit, 56)
    }
    unprocessed[56] = (messageLengthBits ushr 56).toByte()
    unprocessed[57] = (messageLengthBits ushr 48).toByte()
    unprocessed[58] = (messageLengthBits ushr 40).toByte()
    unprocessed[59] = (messageLengthBits ushr 32).toByte()
    unprocessed[60] = (messageLengthBits ushr 24).toByte()
    unprocessed[61] = (messageLengthBits ushr 16).toByte()
    unprocessed[62] = (messageLengthBits ushr  8).toByte()
    unprocessed[63] = (messageLengthBits        ).toByte()
    processChunk(unprocessed, 0)

    val a = h0
    val b = h1
    val c = h2
    val d = h3
    val e = h4
    val f = h5
    val g = h6
    val h = h7

    return byteArrayOf(
      (a shr 24).toByte(),
      (a shr 16).toByte(),
      (a shr  8).toByte(),
      (a       ).toByte(),
      (b shr 24).toByte(),
      (b shr 16).toByte(),
      (b shr  8).toByte(),
      (b       ).toByte(),
      (c shr 24).toByte(),
      (c shr 16).toByte(),
      (c shr  8).toByte(),
      (c       ).toByte(),
      (d shr 24).toByte(),
      (d shr 16).toByte(),
      (d shr  8).toByte(),
      (d       ).toByte(),
      (e shr 24).toByte(),
      (e shr 16).toByte(),
      (e shr  8).toByte(),
      (e       ).toByte(),
      (f shr 24).toByte(),
      (f shr 16).toByte(),
      (f shr  8).toByte(),
      (f       ).toByte(),
      (g shr 24).toByte(),
      (g shr 16).toByte(),
      (g shr  8).toByte(),
      (g       ).toByte(),
      (h shr 24).toByte(),
      (h shr 16).toByte(),
      (h shr  8).toByte(),
      (h       ).toByte()
    )
  }
  /* ktlint-enable */
}
