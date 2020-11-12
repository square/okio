/*
 * Copyright (C) 2020 Square, Inc.
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
package okio.internal

import kotlin.experimental.xor

internal class Hmac(
  private val hashFunction: HashFunction,
  private val outerKey: ByteArray
) {
  fun update(input: ByteArray, offset: Int = 0, byteCount: Int = input.size) {
    hashFunction.update(input, offset, byteCount)
  }

  fun doFinal(): ByteArray {
    val digest = hashFunction.digest()

    hashFunction.update(outerKey)
    hashFunction.update(digest)

    return hashFunction.digest()
  }

  companion object {
    private const val IPAD: Byte = 54
    private const val OPAD: Byte = 92

    fun sha1(key: ByteArray) =
      create(key, hashFunction = Sha1(), blockLength = 64)

    fun sha256(key: ByteArray) =
      create(key, hashFunction = Sha256(), blockLength = 64)

    fun sha512(key: ByteArray) =
      create(key, hashFunction = Sha512(), blockLength = 128)

    private fun create(
      key: ByteArray,
      hashFunction: HashFunction,
      blockLength: Int
    ): Hmac {
      val keySize = key.size
      val paddedKey = when {
        keySize == blockLength -> key
        keySize < blockLength -> key.copyOf(blockLength)
        else -> hashFunction.apply { update(key) }.digest().copyOf(blockLength)
      }

      val innerKey = ByteArray(blockLength) { paddedKey[it] xor IPAD }
      val outerKey = ByteArray(blockLength) { paddedKey[it] xor OPAD }

      hashFunction.update(innerKey)

      return Hmac(
        hashFunction,
        outerKey
      )
    }
  }
}