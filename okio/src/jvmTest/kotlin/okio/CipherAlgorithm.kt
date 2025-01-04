/*
 * Copyright (C) 2020 Square, Inc. and others.
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

enum class CipherAlgorithm(
  val transformation: String,
  val padding: Boolean,
  val keyLength: Int,
  val ivLength: Int? = null,
) {
  AesCbcNopadding("AES/CBC/NoPadding", false, 16, 16),
  AesCbcPkcs5padding("AES/CBC/PKCS5Padding", true, 16, 16),
  AesEcbNopadding("AES/ECB/NoPadding", false, 16),
  AesEcbPkcs5padding("AES/ECB/PKCS5Padding", true, 16),
  DesCbcNopadding("DES/CBC/NoPadding", false, 8, 8),
  DesCbcPkcs5padding("DES/CBC/PKCS5Padding", true, 8, 8),
  DesEcbNopadding("DES/ECB/NoPadding", false, 8),
  DesEcbPkcs5padding("DES/ECB/PKCS5Padding", true, 8),
  DesedeCbcNopadding("DESede/CBC/NoPadding", false, 24, 8),
  DesedeCbcPkcs5padding("DESede/CBC/PKCS5Padding", true, 24, 8),
  DesedeEcbNopadding("DESede/ECB/NoPadding", false, 24),
  DesedeEcbPkcs5padding("DESede/ECB/PKCS5Padding", true, 24),
  ;

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
}
