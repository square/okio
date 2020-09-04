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

private val k = uintArrayOf(
  0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u,
  0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
  0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u,
  0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
  0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu,
  0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
  0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u,
  0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
  0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u,
  0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
  0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u,
  0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
  0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u,
  0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
  0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u,
  0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u
)

internal class Sha256MessageDigest : AbstractMessageDigest<UIntArray>(chunkSize = 64) {

  private val words = UIntArray(64)

  override val hashValues = uintArrayOf(
    0x6a09e667u,
    0xbb67ae85u,
    0x3c6ef372u,
    0xa54ff53au,
    0x510e527fu,
    0x9b05688cu,
    0x1f83d9abu,
    0x5be0cd19u
  )

  override fun processChunk(array: ByteArray, offset: Int) {
    var offset = offset

    for (i in 0 until 16) {
      words[i] = bytesToBigEndianUInt(
        array[offset++],
        array[offset++],
        array[offset++],
        array[offset++]
      )
    }

    for (i in 16 until 64) {
      val s0 = (words[i - 15] rightRotate 7) xor (words[i - 15] rightRotate 18) xor (words[i - 15] shr 3)
      val s1 = (words[i - 2] rightRotate 17) xor (words[i - 2] rightRotate 19) xor (words[i - 2] shr 10)
      words[i] = words[i - 16] + s0 + words[i - 7] + s1
    }

    var (a, b, c, d, e, f, g, h) = hashValues

    for (i in 0 until 64) {
      val s0 = (a rightRotate 2) xor (a rightRotate 13) xor (a rightRotate 22)
      val s1 = (e rightRotate 6) xor (e rightRotate 11) xor (e rightRotate 25)

      val ch = (e and f) xor (e.inv() and g)
      val maj = (a and b) xor (a and c) xor (b and c)

      val t1 = h + s1 + ch + k[i] + words[i]
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

    hashValues[0] = hashValues[0] + a
    hashValues[1] = hashValues[1] + b
    hashValues[2] = hashValues[2] + c
    hashValues[3] = hashValues[3] + d
    hashValues[4] = hashValues[4] + e
    hashValues[5] = hashValues[5] + f
    hashValues[6] = hashValues[6] + g
    hashValues[7] = hashValues[7] + h
  }

  override fun hasValuesToByteArray() = hashValues.toBigEndianByteArray()
}
