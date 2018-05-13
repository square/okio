/*
 * Copyright (C) 2018 Square, Inc.
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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class HashingSourceTest {
  @Test fun md5() {
    val data = Buffer().writeUtf8("Hi!")
    val hash = (data as Source).hashMd5()
    assertThat(Okio.buffer(hash).readUtf8()).isEqualTo("Hi!")
    assertThat(hash.hash().hex()).isEqualTo("5360706c803a759e3a9f2ca54a651950")
  }

  @Test fun sha1() {
    val data = Buffer().writeUtf8("Hi!")
    val hash = (data as Source).hashSha1()
    assertThat(Okio.buffer(hash).readUtf8()).isEqualTo("Hi!")
    assertThat(hash.hash().hex()).isEqualTo("c0a0ad26a634840c67a210fefdda76577b03a111")
  }

  @Test fun sha256() {
    val data = Buffer().writeUtf8("Hi!")
    val hash = (data as Source).hashSha256()
    assertThat(Okio.buffer(hash).readUtf8()).isEqualTo("Hi!")
    assertThat(hash.hash().hex()).isEqualTo(
        "ca51ce1fb15acc6d69b8a5700256172fcc507e02073e6f19592e341bd6508ab8")
  }

  @Test fun sha512() {
    val data = Buffer().writeUtf8("Hi!")
    val hash = (data as Source).hashSha512()
    assertThat(Okio.buffer(hash).readUtf8()).isEqualTo("Hi!")
    assertThat(hash.hash().hex()).isEqualTo(
        "9e6ffbc90cbd9f745bda9b5c0451617db9513cb7a19e1e5b25a141e951515715" +
        "68c951371336cb8bd92957e2684103a4e3bc987fa2c14f5d436037b84ac3b529")
  }

  @Test fun hmacSha1() {
    val data = Buffer().writeUtf8("Hi!")
    val hash = (data as Source).hashHmacSha1(ByteString.encodeUtf8("donut"))
    assertThat(Okio.buffer(hash).readUtf8()).isEqualTo("Hi!")
    assertThat(hash.hash().hex()).isEqualTo("a2a1da01d1c973f6433383e2e8bb3820f1d07a41")
  }

  @Test fun hmacSha256() {
    val data = Buffer().writeUtf8("Hi!")
    val hash = (data as Source).hashHmacSha256(ByteString.encodeUtf8("donut"))
    assertThat(Okio.buffer(hash).readUtf8()).isEqualTo("Hi!")
    assertThat(hash.hash().hex()).isEqualTo(
        "7fdb975ed2f9159d2f724b8820e8562e50ac5c4b96fb57c08cccd14154742dde")
  }

  @Test fun hmacSha512() {
    val data = Buffer().writeUtf8("Hi!")
    val hash = (data as Source).hashHmacSha512(ByteString.encodeUtf8("donut"))
    assertThat(Okio.buffer(hash).readUtf8()).isEqualTo("Hi!")
    assertThat(hash.hash().hex()).isEqualTo(
        "e80a569ad58dd988b7cf9c047fbffe434cdef9c95172bd05a7ade50809fc0880" +
        "b207bfe461e5c7db250aa5d9489f3555c97d4505651695fd459ea462e4ec3f73")
  }
}
