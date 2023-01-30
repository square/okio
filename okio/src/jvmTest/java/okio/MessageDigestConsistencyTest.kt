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
package okio

import java.security.MessageDigest
import java.util.Random
import kotlin.test.Test
import okio.ByteString.Companion.toByteString
import okio.internal.HashFunction
import okio.internal.Md5
import okio.internal.Sha1
import okio.internal.Sha256
import okio.internal.Sha512
import org.assertj.core.api.Assertions.assertThat

/**
 * Confirm Okio is consistent with the JDK's MessageDigest algorithms for various sizes and slices.
 * This makes repeated calls to update() with byte arrays of various sizes and contents to defend
 * against bugs in batching inputs.
 */
class MessageDigestConsistencyTest {
  @Test fun sha1() {
    test("SHA-1") { Sha1() }
  }

  @Test fun sha256() {
    test("SHA-256") { Sha256() }
  }

  @Test fun sha512() {
    test("SHA-512") { Sha512() }
  }

  @Test fun md5() {
    test("MD5") { Md5() }
  }

  private fun test(algorithm: String, newHashFunction: () -> HashFunction) {
    for (seed in 0L until 1000L) {
      for (updateCount in 0 until 10) {
        test(
          algorithm = algorithm,
          hashFunction = newHashFunction(),
          seed = seed,
          updateCount = updateCount,
        )
      }
    }
  }

  private fun test(
    algorithm: String,
    hashFunction: HashFunction,
    seed: Long,
    updateCount: Int,
  ) {
    val data = Buffer()

    val random = Random(seed)
    for (i in 0 until updateCount) {
      val size = random.nextInt(1000) + 1 // size must be >= 1.
      val byteArray = ByteArray(size).also { random.nextBytes(it) }
      val offset = random.nextInt(size)
      val byteCount = random.nextInt(size - offset)

      hashFunction.update(
        input = byteArray,
        offset = offset,
        byteCount = byteCount,
      )

      data.write(
        source = byteArray,
        offset = offset,
        byteCount = byteCount,
      )
    }

    val okioHash = hashFunction.digest()

    val byteArray = data.readByteArray()
    val jdkMessageDigest = MessageDigest.getInstance(algorithm)
    val jdkHash = jdkMessageDigest.digest(byteArray)

    assertThat(okioHash.toByteString()).isEqualTo(jdkHash.toByteString())
  }
}
