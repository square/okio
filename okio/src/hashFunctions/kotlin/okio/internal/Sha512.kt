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

internal class Sha512 : HashFunction {
  private var messageLength = 0L
  private val unprocessed = ByteArray(128)
  private var unprocessedLimit = 0
  private val words = LongArray(80)

  private var h0 = 7640891576956012808L
  private var h1 = -4942790177534073029L
  private var h2 = 4354685564936845355L
  private var h3 = -6534734903238641935L
  private var h4 = 5840696475078001361L
  private var h5 = -7276294671716946913L
  private var h6 = 2270897969802886507L
  private var h7 = 6620516959819538809L

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

    reset()

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

  private fun reset() {
    messageLength = 0L
    unprocessed.fill(0)
    unprocessedLimit = 0
    words.fill(0)

    h0 = 7640891576956012808L
    h1 = -4942790177534073029L
    h2 = 4354685564936845355L
    h3 = -6534734903238641935L
    h4 = 5840696475078001361L
    h5 = -7276294671716946913L
    h6 = 2270897969802886507L
    h7 = 6620516959819538809L
  }

  companion object {
    private val k = longArrayOf(
      4794697086780616226L, 8158064640168781261L, -5349999486874862801L, -1606136188198331460L,
      4131703408338449720L, 6480981068601479193L, -7908458776815382629L, -6116909921290321640L,
      -2880145864133508542L, 1334009975649890238L, 2608012711638119052L, 6128411473006802146L,
      8268148722764581231L, -9160688886553864527L, -7215885187991268811L, -4495734319001033068L,
      -1973867731355612462L, -1171420211273849373L, 1135362057144423861L, 2597628984639134821L,
      3308224258029322869L, 5365058923640841347L, 6679025012923562964L, 8573033837759648693L,
      -7476448914759557205L, -6327057829258317296L, -5763719355590565569L, -4658551843659510044L,
      -4116276920077217854L, -3051310485924567259L, 489312712824947311L, 1452737877330783856L,
      2861767655752347644L, 3322285676063803686L, 5560940570517711597L, 5996557281743188959L,
      7280758554555802590L, 8532644243296465576L, -9096487096722542874L, -7894198246740708037L,
      -6719396339535248540L, -6333637450476146687L, -4446306890439682159L, -4076793802049405392L,
      -3345356375505022440L, -2983346525034927856L, -860691631967231958L, 1182934255886127544L,
      1847814050463011016L, 2177327727835720531L, 2830643537854262169L, 3796741975233480872L,
      4115178125766777443L, 5681478168544905931L, 6601373596472566643L, 7507060721942968483L,
      8399075790359081724L, 8693463985226723168L, -8878714635349349518L, -8302665154208450068L,
      -8016688836872298968L, -6606660893046293015L, -4685533653050689259L, -4147400797238176981L,
      -3880063495543823972L, -3348786107499101689L, -1523767162380948706L, -757361751448694408L,
      500013540394364858L, 748580250866718886L, 1242879168328830382L, 1977374033974150939L,
      2944078676154940804L, 3659926193048069267L, 4368137639120453308L, 4836135668995329356L,
      5532061633213252278L, 6448918945643986474L, 6902733635092675308L, 7801388544844847127L,
    )
  }
}
