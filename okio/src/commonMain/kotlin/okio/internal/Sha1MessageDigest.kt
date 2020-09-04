/*
 * Copyright (C) 2018 Square, Inc.
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

@ExperimentalUnsignedTypes
internal class Sha1MessageDigest : AbstractMessageDigest<UIntArray>(chunkSize = 64) {

  private val words = UIntArray(80)

  override val hashValues = uintArrayOf(
    0x67452301u,
    0xEFCDAB89u,
    0x98BADCFEu,
    0x10325476u,
    0xC3D2E1F0u
  )

  override fun processChunk(array: ByteArray, offset: Int) {
    var w = 0
    var offset = offset
    while (w < 16) {
      words[w++] = bytesToBigEndianUInt(
        array[offset++],
        array[offset++],
        array[offset++],
        array[offset++]
      )
    }

    while (w < 80) {
      words[w] = (words[w - 3] xor words[w - 8] xor words[w - 14] xor words[w - 16]) leftRotate 1
      w++
    }

    var (a, b, c, d, e) = hashValues

    for (i in 0 until 80) {
      val f: UInt
      val k: UInt

      when {
        i < 20 -> {
          f = (d xor (b and (c xor d)))
          k = 0x5A827999u
        }
        i < 40 -> {
          f = (b xor c xor d)
          k = 0x6ED9EBA1u
        }
        i < 60 -> {
          f = ((b and c) or (b and d) or (c and d))
          k = 0x8F1BBCDCu
        }
        else -> {
          f = (b xor c xor d)
          k = 0xCA62C1D6u
        }
      }

      val a2 = ((a leftRotate 5) + f + e + k + words[i])
      val b2 = a
      val c2 = b leftRotate 30
      val d2 = c
      val e2 = d

      a = a2
      b = b2
      c = c2
      d = d2
      e = e2
    }

    hashValues[0] = hashValues[0] + a
    hashValues[1] = hashValues[1] + b
    hashValues[2] = hashValues[2] + c
    hashValues[3] = hashValues[3] + d
    hashValues[4] = hashValues[4] + e
  }

  override fun hasValuesToByteArray() = hashValues.toBigEndianByteArray()
}
