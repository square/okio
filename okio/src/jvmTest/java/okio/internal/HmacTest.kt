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

import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Check the [Hmac] implementation against the reference [Mac] JVM implementation.
 */
@RunWith(Parameterized::class)
class HmacTest(val parameters: Parameters) {

  companion object {
    @get:Parameterized.Parameters(name = "{0}")
    @get:JvmStatic
    val parameters: List<Parameters>
      get() {
        val algorithms = enumValues<Parameters.Algorithm>()
        val keySizes = listOf(8, 32, 48, 64, 128, 256)
        val dataSizes = listOf(0, 32, 64, 128, 256, 512)
        return algorithms.flatMap { algorithm ->
          keySizes.flatMap { keySize ->
            dataSizes.map { dataSize ->
              Parameters(
                algorithm,
                keySize,
                dataSize
              )
            }
          }
        }
      }
  }

  private val keySize
    get() = parameters.keySize
  private val dataSize
    get() = parameters.dataSize
  private val algorithm
    get() = parameters.algorithmName

  private val random = Random(682741861446)

  private val key = random.nextBytes(keySize)
  private val bytes = random.nextBytes(dataSize)
  private val mac = parameters.createMac(key)

  private val expected = hmac(algorithm, key, bytes)

  @Test
  fun hmac() {
    mac.update(bytes)
    val hmacValue = mac.digest()

    assertArrayEquals(expected, hmacValue)
  }

  @Test
  fun hmacBytes() {
    for (byte in bytes) {
      mac.update(byteArrayOf(byte))
    }
    val hmacValue = mac.digest()

    assertArrayEquals(expected, hmacValue)
  }

  data class Parameters(
    val algorithm: Algorithm,
    val keySize: Int,
    val dataSize: Int,
  ) {
    val algorithmName
      get() = algorithm.algorithmName

    internal fun createMac(key: ByteArray) =
      algorithm.HmacFactory(ByteString(key))

    enum class Algorithm(
      val algorithmName: String,
      internal val HmacFactory: (key: ByteString) -> Hmac
    ) {
      SHA_1("HmacSha1", Hmac.Companion::sha1),
      SHA_256("HmacSha256", Hmac.Companion::sha256),
      SHA_512("HmacSha512", Hmac.Companion::sha512),
    }
  }
}

private fun hmac(algorithm: String, key: ByteArray, bytes: ByteArray) =
  Mac.getInstance(algorithm).apply { init(SecretKeySpec(key, algorithm)) }.doFinal(bytes)
