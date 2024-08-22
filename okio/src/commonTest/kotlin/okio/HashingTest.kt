/*
 * Copyright 2014 Square Inc.
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

import kotlin.test.Test
import kotlin.test.assertEquals
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

class HashingTest {
  @Test fun byteStringMd5() {
    assertEquals(MD5_abc, "abc".encodeUtf8().md5())
  }

  @Test fun byteStringSha1() {
    assertEquals(SHA1_abc, "abc".encodeUtf8().sha1())
  }

  @Test fun byteStringSha256() {
    assertEquals(SHA256_abc, "abc".encodeUtf8().sha256())
  }

  @Test fun byteStringSha512() {
    assertEquals(SHA512_abc, "abc".encodeUtf8().sha512())
  }

  @Test fun byteStringHmacSha1() {
    assertEquals(HMAC_SHA1_abc, "abc".encodeUtf8().hmacSha1(HMAC_KEY))
  }

  @Test fun byteStringHmacSha256() {
    assertEquals(HMAC_SHA256_abc, "abc".encodeUtf8().hmacSha256(HMAC_KEY))
  }

  @Test fun byteStringHmacSha512() {
    assertEquals(HMAC_SHA512_abc, "abc".encodeUtf8().hmacSha512(HMAC_KEY))
  }

  @Test fun bufferMd5() {
    assertEquals(MD5_abc, Buffer().writeUtf8("abc").md5())
  }

  @Test fun bufferSha1() {
    assertEquals(SHA1_abc, Buffer().writeUtf8("abc").sha1())
  }

  @Test fun bufferSha256() {
    assertEquals(SHA256_abc, Buffer().writeUtf8("abc").sha256())
  }

  @Test fun bufferSha512() {
    assertEquals(SHA512_abc, Buffer().writeUtf8("abc").sha512())
  }

  @Test fun hashEmptySha256Buffer() {
    assertEquals(SHA256_empty, Buffer().sha256())
  }

  @Test fun hashEmptySha512Buffer() {
    assertEquals(SHA512_empty, Buffer().sha512())
  }

  @Test fun bufferHmacSha1() {
    assertEquals(HMAC_SHA1_abc, Buffer().writeUtf8("abc").hmacSha1(HMAC_KEY))
  }

  @Test fun bufferHmacSha256() {
    assertEquals(HMAC_SHA256_abc, Buffer().writeUtf8("abc").hmacSha256(HMAC_KEY))
  }

  @Test fun bufferHmacSha512() {
    assertEquals(HMAC_SHA512_abc, Buffer().writeUtf8("abc").hmacSha512(HMAC_KEY))
  }

  @Test fun hmacSha256EmptyBuffer() {
    assertEquals(HMAC_SHA256_empty, Buffer().hmacSha256(HMAC_KEY))
  }

  @Test fun hmacSha512EmptyBuffer() {
    assertEquals(HMAC_SHA512_empty, Buffer().hmacSha512(HMAC_KEY))
  }

  @Test fun bufferHashIsNotDestructive() {
    val buffer = Buffer()

    buffer.writeUtf8("abc")
    assertEquals(SHA256_abc, buffer.sha256())
    assertEquals("abc", buffer.readUtf8())

    buffer.writeUtf8("def")
    assertEquals(SHA256_def, buffer.sha256())
    assertEquals("def", buffer.readUtf8())

    buffer.write(r32k)
    assertEquals(SHA256_r32k, buffer.sha256())
    assertEquals(r32k, buffer.readByteString())
  }

  companion object {
    val HMAC_KEY =
      "0102030405060708".decodeHex()
    val MD5_abc =
      "900150983cd24fb0d6963f7d28e17f72".decodeHex()
    val SHA1_abc =
      "a9993e364706816aba3e25717850c26c9cd0d89d".decodeHex()
    val HMAC_SHA1_abc =
      "987af8649982ff7d9fbb1b8aa35099146997af51".decodeHex()
    val SHA256_abc =
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".decodeHex()
    val SHA256_empty =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".decodeHex()
    val SHA256_def =
      "cb8379ac2098aa165029e3938a51da0bcecfc008fd6795f401178647f96c5b34".decodeHex()
    val SHA256_r32k =
      "dadec7297f49bdf219895bd9942454047d394e1f20f247fbdc591080b4e8731e".decodeHex()
    val SHA512_abc =
      "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f".decodeHex()
    val SHA512_empty =
      "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e".decodeHex()
    val HMAC_SHA256_empty =
      "9eeecd4a51b7e5cbfcd63bfa89130944d314c20b5c79979b124143fea006452a".decodeHex()
    val HMAC_SHA256_abc =
      "446d1715583cf1c30dfffbec0df4ff1f9d39d493211ab4c97ed6f3f0eb579b47".decodeHex()
    val HMAC_SHA512_empty =
      "c0bd671885fa6f2eade99e9b81bbc74b8c6aa9ee9e58d7e5c356022d2f0c1cd7a0c75124b88a1a021e4323ce781846d246a379df78c3b955461d1688cc873335".decodeHex()
    val HMAC_SHA512_abc =
      "24391790e7131050b05b606f2079a8983313894a1642a5ed97d094e7cabd00cfaa857d92c1f320ca3b6aaabb84c7155d6f1b10940dc133ded1b40baee8900be6".decodeHex()
    val r32k = randomBytes(32768)
  }
}
