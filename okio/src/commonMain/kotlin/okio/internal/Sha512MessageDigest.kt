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

import okio.rightRotate

@ExperimentalUnsignedTypes
private val k = longArrayOf(
  0x428a2f98d728ae22UL.toLong(), 0x7137449123ef65cdUL.toLong(), 0xb5c0fbcfec4d3b2fUL.toLong(), 0xe9b5dba58189dbbcUL.toLong(), 0x3956c25bf348b538UL.toLong(),
  0x59f111f1b605d019UL.toLong(), 0x923f82a4af194f9bUL.toLong(), 0xab1c5ed5da6d8118UL.toLong(), 0xd807aa98a3030242UL.toLong(), 0x12835b0145706fbeUL.toLong(),
  0x243185be4ee4b28cUL.toLong(), 0x550c7dc3d5ffb4e2UL.toLong(), 0x72be5d74f27b896fUL.toLong(), 0x80deb1fe3b1696b1UL.toLong(), 0x9bdc06a725c71235UL.toLong(),
  0xc19bf174cf692694UL.toLong(), 0xe49b69c19ef14ad2UL.toLong(), 0xefbe4786384f25e3UL.toLong(), 0x0fc19dc68b8cd5b5UL.toLong(), 0x240ca1cc77ac9c65UL.toLong(),
  0x2de92c6f592b0275UL.toLong(), 0x4a7484aa6ea6e483UL.toLong(), 0x5cb0a9dcbd41fbd4UL.toLong(), 0x76f988da831153b5UL.toLong(), 0x983e5152ee66dfabUL.toLong(),
  0xa831c66d2db43210UL.toLong(), 0xb00327c898fb213fUL.toLong(), 0xbf597fc7beef0ee4UL.toLong(), 0xc6e00bf33da88fc2UL.toLong(), 0xd5a79147930aa725UL.toLong(),
  0x06ca6351e003826fUL.toLong(), 0x142929670a0e6e70UL.toLong(), 0x27b70a8546d22ffcUL.toLong(), 0x2e1b21385c26c926UL.toLong(), 0x4d2c6dfc5ac42aedUL.toLong(),
  0x53380d139d95b3dfUL.toLong(), 0x650a73548baf63deUL.toLong(), 0x766a0abb3c77b2a8UL.toLong(), 0x81c2c92e47edaee6UL.toLong(), 0x92722c851482353bUL.toLong(),
  0xa2bfe8a14cf10364UL.toLong(), 0xa81a664bbc423001UL.toLong(), 0xc24b8b70d0f89791UL.toLong(), 0xc76c51a30654be30UL.toLong(), 0xd192e819d6ef5218UL.toLong(),
  0xd69906245565a910UL.toLong(), 0xf40e35855771202aUL.toLong(), 0x106aa07032bbd1b8UL.toLong(), 0x19a4c116b8d2d0c8UL.toLong(), 0x1e376c085141ab53UL.toLong(),
  0x2748774cdf8eeb99UL.toLong(), 0x34b0bcb5e19b48a8UL.toLong(), 0x391c0cb3c5c95a63UL.toLong(), 0x4ed8aa4ae3418acbUL.toLong(), 0x5b9cca4f7763e373UL.toLong(),
  0x682e6ff3d6b2b8a3UL.toLong(), 0x748f82ee5defb2fcUL.toLong(), 0x78a5636f43172f60UL.toLong(), 0x84c87814a1f0ab72UL.toLong(), 0x8cc702081a6439ecUL.toLong(),
  0x90befffa23631e28UL.toLong(), 0xa4506cebde82bde9UL.toLong(), 0xbef9a3f7b2c67915UL.toLong(), 0xc67178f2e372532bUL.toLong(), 0xca273eceea26619cUL.toLong(),
  0xd186b8c721c0c207UL.toLong(), 0xeada7dd6cde0eb1eUL.toLong(), 0xf57d4f7fee6ed178UL.toLong(), 0x06f067aa72176fbaUL.toLong(), 0x0a637dc5a2c898a6UL.toLong(),
  0x113f9804bef90daeUL.toLong(), 0x1b710b35131c471bUL.toLong(), 0x28db77f523047d84UL.toLong(), 0x32caab7b40c72493UL.toLong(), 0x3c9ebe0a15c9bebcUL.toLong(),
  0x431d67c49c100d4cUL.toLong(), 0x4cc5d4becb3e42b6UL.toLong(), 0x597f299cfc657e2aUL.toLong(), 0x5fcb6fab3ad6faecUL.toLong(), 0x6c44198c4a475817UL.toLong()
)

@ExperimentalUnsignedTypes
internal class Sha512MessageDigest : OkioMessageDigest {
  private var messageLength = 0L
  private val unprocessed = ByteArray(128)
  private var unprocessedLimit = 0
  private val words = LongArray(80)

  private var h0 = 0x6a09e667f3bcc908UL.toLong()
  private var h1 = 0xbb67ae8584caa73bUL.toLong()
  private var h2 = 0x3c6ef372fe94f82bUL.toLong()
  private var h3 = 0xa54ff53a5f1d36f1UL.toLong()
  private var h4 = 0x510e527fade682d1UL.toLong()
  private var h5 = 0x9b05688c2b3e6c1fUL.toLong()
  private var h6 = 0x1f83d9abfb41bd6bUL.toLong()
  private var h7 = 0x5be0cd19137e2179UL.toLong()

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
      if (unprocessedLimit + byteCount < 128) {
        // Not enough bytes for a chunk.
        input.copyInto(unprocessed, unprocessedLimit, pos, limit)
        this.unprocessedLimit = unprocessedLimit + byteCount
        return
      }

      // Process a chunk combining leftover bytes and the input.
      val consumeByteCount = 128 - unprocessedLimit
      input.copyInto(unprocessed, unprocessedLimit, pos, pos + consumeByteCount)
      processChunk(unprocessed, 0)
      this.unprocessedLimit = 0
      pos += consumeByteCount
    }

    while (pos < limit) {
      val nextPos = pos + 128

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
      words[w] = ((input[pos++].toLong() and 0xff) shl 56) or
          ((input[pos++].toLong() and 0xff) shl 48) or
          ((input[pos++].toLong() and 0xff) shl 40) or
          ((input[pos++].toLong() and 0xff) shl 32) or
          ((input[pos++].toLong() and 0xff) shl 24) or
          ((input[pos++].toLong() and 0xff) shl 16) or
          ((input[pos++].toLong() and 0xff) shl 8) or
          ((input[pos++].toLong() and 0xff))
    }

    for (i in 16 until 80) {
      val w15 = words[i - 15]
      val s0 = (w15 rightRotate 1) xor (w15 rightRotate 8) xor (w15 ushr 7)
      val w2 = words[i - 2]
      val s1 = (w2 rightRotate 19) xor (w2 rightRotate 61) xor (w2 ushr 6)
      val w16 = words[i - 16]
      val w7 = words[i - 7]
      words[i] = w16 + s0 + w7 + s1
    }

    hash(words)
  }

  private fun hash(words: LongArray) {
    val localK = k
    var a = h0
    var b = h1
    var c = h2
    var d = h3
    var e = h4
    var f = h5
    var g = h6
    var h = h7

    for (i in 0 until 80) {
      val s0 = (a rightRotate 28) xor (a rightRotate 34) xor (a rightRotate 39)
      val s1 = (e rightRotate 14) xor (e rightRotate 18) xor (e rightRotate 41)

      val ch = (e and f) xor (e.inv() and g)
      val maj = (a and b) xor (a and c) xor (b and c)

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
    if (unprocessedLimit > 112) {
      unprocessed.fill(0, unprocessedLimit, 128)
      processChunk(unprocessed, 0)
      unprocessed.fill(0, 0, unprocessedLimit)
    } else {
      unprocessed.fill(0, unprocessedLimit, 120)
    }
    unprocessed[120] = (messageLengthBits ushr 56).toByte()
    unprocessed[121] = (messageLengthBits ushr 48).toByte()
    unprocessed[122] = (messageLengthBits ushr 40).toByte()
    unprocessed[123] = (messageLengthBits ushr 32).toByte()
    unprocessed[124] = (messageLengthBits ushr 24).toByte()
    unprocessed[125] = (messageLengthBits ushr 16).toByte()
    unprocessed[126] = (messageLengthBits ushr  8).toByte()
    unprocessed[127] = (messageLengthBits        ).toByte()
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
      (a shr 56).toByte(),
      (a shr 48).toByte(),
      (a shr 40).toByte(),
      (a shr 32).toByte(),
      (a shr 24).toByte(),
      (a shr 16).toByte(),
      (a shr  8).toByte(),
      (a       ).toByte(),
      (b shr 56).toByte(),
      (b shr 48).toByte(),
      (b shr 40).toByte(),
      (b shr 32).toByte(),
      (b shr 24).toByte(),
      (b shr 16).toByte(),
      (b shr  8).toByte(),
      (b       ).toByte(),
      (c shr 56).toByte(),
      (c shr 48).toByte(),
      (c shr 40).toByte(),
      (c shr 32).toByte(),
      (c shr 24).toByte(),
      (c shr 16).toByte(),
      (c shr  8).toByte(),
      (c       ).toByte(),
      (d shr 56).toByte(),
      (d shr 48).toByte(),
      (d shr 40).toByte(),
      (d shr 32).toByte(),
      (d shr 24).toByte(),
      (d shr 16).toByte(),
      (d shr  8).toByte(),
      (d       ).toByte(),
      (e shr 56).toByte(),
      (e shr 48).toByte(),
      (e shr 40).toByte(),
      (e shr 32).toByte(),
      (e shr 24).toByte(),
      (e shr 16).toByte(),
      (e shr  8).toByte(),
      (e       ).toByte(),
      (f shr 56).toByte(),
      (f shr 48).toByte(),
      (f shr 40).toByte(),
      (f shr 32).toByte(),
      (f shr 24).toByte(),
      (f shr 16).toByte(),
      (f shr  8).toByte(),
      (f       ).toByte(),
      (g shr 56).toByte(),
      (g shr 48).toByte(),
      (g shr 40).toByte(),
      (g shr 32).toByte(),
      (g shr 24).toByte(),
      (g shr 16).toByte(),
      (g shr  8).toByte(),
      (g       ).toByte(),
      (h shr 56).toByte(),
      (h shr 48).toByte(),
      (h shr 40).toByte(),
      (h shr 32).toByte(),
      (h shr 24).toByte(),
      (h shr 16).toByte(),
      (h shr  8).toByte(),
      (h       ).toByte()
    )
  }
  /* ktlint-enable */
}
