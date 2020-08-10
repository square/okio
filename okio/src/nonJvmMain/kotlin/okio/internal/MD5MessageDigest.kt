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

import okio.OkioMessageDigest

private val s = intArrayOf(
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
  5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
  4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
  6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
)

private val k = uintArrayOf(
  0xd76aa478u, 0xe8c7b756u, 0x242070dbu, 0xc1bdceeeu,
  0xf57c0fafu, 0x4787c62au, 0xa8304613u, 0xfd469501u,
  0x698098d8u, 0x8b44f7afu, 0xffff5bb1u, 0x895cd7beu,
  0x6b901122u, 0xfd987193u, 0xa679438eu, 0x49b40821u,
  0xf61e2562u, 0xc040b340u, 0x265e5a51u, 0xe9b6c7aau,
  0xd62f105du, 0x02441453u, 0xd8a1e681u, 0xe7d3fbc8u,
  0x21e1cde6u, 0xc33707d6u, 0xf4d50d87u, 0x455a14edu,
  0xa9e3e905u, 0xfcefa3f8u, 0x676f02d9u, 0x8d2a4c8au,
  0xfffa3942u, 0x8771f681u, 0x6d9d6122u, 0xfde5380cu,
  0xa4beea44u, 0x4bdecfa9u, 0xf6bb4b60u, 0xbebfbc70u,
  0x289b7ec6u, 0xeaa127fau, 0xd4ef3085u, 0x04881d05u,
  0xd9d4d039u, 0xe6db99e5u, 0x1fa27cf8u, 0xc4ac5665u,
  0xf4292244u, 0x432aff97u, 0xab9423a7u, 0xfc93a039u,
  0x655b59c3u, 0x8f0ccc92u, 0xffeff47du, 0x85845dd1u,
  0x6fa87e4fu, 0xfe2ce6e0u, 0xa3014314u, 0x4e0811a1u,
  0xf7537e82u, 0xbd3af235u, 0x2ad7d2bbu, 0xeb86d391u
)

internal class MD5MessageDigest : OkioMessageDigest {

  private var messageLength = 0L
  private var unprocessed = Bytes.EMPTY
  private var currentDigest = HashDigest(
    0x67452301u,
    0xefcdab89u,
    0x98badcfeu,
    0x10325476u
  )

  override fun update(input: ByteArray) {
    for (chunk in (unprocessed + input.toBytes()).chunked(64)) {
      when (chunk.size) {
        64 -> {
          currentDigest = processChunk(chunk, currentDigest)
          messageLength += 64
        }
        else -> unprocessed = chunk
      }
    }
  }

  override fun digest(): ByteArray {
    val finalMessageLength = messageLength + unprocessed.size

    val finalMessage = byteArrayOf(
      *unprocessed.toByteArray(),
      0x80.toByte(),
      *ByteArray((56 - (finalMessageLength + 1) absMod 64).toInt()),
      *(finalMessageLength * 8L).toLittleEndianByteArray()
    ).toBytes()

    finalMessage.chunked(64).forEach { chunk ->
      currentDigest = processChunk(chunk, currentDigest)
    }

    return currentDigest.toLittleEndianByteArray()
  }

  private fun processChunk(chunk: Bytes, currentDigest: HashDigest): HashDigest {
    require(chunk.size == 64)

    val words = UIntArray(16)
    chunk.chunked(4).forEachIndexed { index, bytes ->
      words[index] = bytes.toLittleEndianUInt()
    }

    var (a, b, c, d) = currentDigest
    for (i in 0 until 64) {
      var (f: UInt, g) = when (i) {
        in 0 until 16 -> Pair(
          (b and c) or (b.inv() and d),
          i
        )
        in 16 until 32 -> Pair(
          (d and b) or (d.inv() and c),
          ((5 * i) + 1) % 16
        )
        in 32 until 48 -> Pair(
          b xor c xor d,
          ((3 * i) + 5) % 16
        )
        in 48 until 64 -> Pair(
          c xor (b or d.inv()),
          (7 * i) % 16
        )
        else -> error("Index is wonky, this should never happen")
      }

      f = f + a + k[i] + words[g]
      a = d
      d = c
      c = b
      b = b + (f leftRotate s[i])
    }

    return HashDigest(
      currentDigest[0] + a,
      currentDigest[1] + b,
      currentDigest[2] + c,
      currentDigest[3] + d
    )
  }
}