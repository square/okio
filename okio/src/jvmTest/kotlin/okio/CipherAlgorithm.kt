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
package okio

import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

data class CipherAlgorithm(
  val transformation: String,
  val padding: Boolean,
  val keyLength: Int,
  val ivLength: Int? = null,
) {
  fun createCipherFactory(random: Random): CipherFactory {
    val key = random.nextBytes(keyLength)
    val secretKeySpec = SecretKeySpec(key, transformation.substringBefore('/'))
    return if (ivLength == null) {
      CipherFactory(transformation) { mode ->
        init(mode, secretKeySpec)
      }
    } else {
      val iv = random.nextBytes(ivLength)
      val ivParameterSpec = IvParameterSpec(iv)
      CipherFactory(transformation) { mode ->
        init(mode, secretKeySpec, ivParameterSpec)
      }
    }
  }

  override fun toString() = transformation

  companion object {
    val BLOCK_CIPHER_ALGORITHMS
      get() = listOf(
        CipherAlgorithm("AES/CBC/NoPadding", false, 16, 16),
        CipherAlgorithm("AES/CBC/PKCS5Padding", true, 16, 16),
        CipherAlgorithm("AES/ECB/NoPadding", false, 16),
        CipherAlgorithm("AES/ECB/PKCS5Padding", true, 16),
        CipherAlgorithm("DES/CBC/NoPadding", false, 8, 8),
        CipherAlgorithm("DES/CBC/PKCS5Padding", true, 8, 8),
        CipherAlgorithm("DES/ECB/NoPadding", false, 8),
        CipherAlgorithm("DES/ECB/PKCS5Padding", true, 8),
        CipherAlgorithm("DESede/CBC/NoPadding", false, 24, 8),
        CipherAlgorithm("DESede/CBC/PKCS5Padding", true, 24, 8),
        CipherAlgorithm("DESede/ECB/NoPadding", false, 24),
        CipherAlgorithm("DESede/ECB/PKCS5Padding", true, 24),
      )
  }
}
