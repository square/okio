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

import okio.internal.OkioMessageDigest
import okio.internal.commonAsUtf8ToByteArray
import okio.internal.newMessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

class OkioMessageDigestTest {

  // region SHA-1

  @Test fun sha1EmptyValueHashIsCorrect() {
    val result = newMessageDigest("SHA-1").apply {
      update(byteArrayOf())
    }.digest()

    assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", ByteString(result).hex())
  }

  @Test fun sha1SimpleValueHashIsCorrect() {
    val value = "Kevin".commonAsUtf8ToByteArray()
    val result = newMessageDigest("SHA-1").apply {
      update(value)
    }.digest()

    assertEquals("e043899daa0c7add37bc99792b2c045d6abbc6dc", ByteString(result).hex())
  }

  @Test fun sha1PaddingSizeValueisCorrect() {
    val result = newMessageDigest("SHA-1").apply {
      update(ByteArray(56) { 'i'.toByte() })
    }.digest()

    assertEquals("86ef7804dacb2161841bc60a26f21d71cf3f349f", ByteString(result).hex())
  }

  @Test fun sha1ChunkSizeValueHashIsCorrect() {
    val result = newMessageDigest("SHA-1").apply {
      update(ByteArray(64) { 'i'.toByte() })
    }.digest()

    assertEquals("79c64455d4565a82bc3f4ec5d9a5e8443c2e77b3", ByteString(result).hex())
  }

  @Test fun sha1ValueLargerThanChunkHashIsCorrect() {
    val result = newMessageDigest("SHA-1").apply {
      update(ByteArray(65) { 'i'.toByte() })
    }.digest()

    assertEquals("6658b01c97fd6db0bdb010b2e154164285e5bd71", ByteString(result).hex())
  }

  @Test fun sha1ComplexValueHashIsCorrect() {
    val value = "The quick brown fox jumps over the lazy dog".commonAsUtf8ToByteArray()
    val result = newMessageDigest("SHA-1").apply {
      update(value)
    }.digest()

    assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12", ByteString(result).hex())
  }

  // endregion

  // region SHA-256

  @Test fun sha256EmptyValueHashIsCorrect() {
    val result = newMessageDigest("SHA-256").apply {
      update(byteArrayOf())
    }.digest()

    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ByteString(result).hex())
  }

  @Test fun sha256SimpleValueHashIsCorrect() {
    val value = "hello world".commonAsUtf8ToByteArray()
    val result = newMessageDigest("SHA-256").apply {
      update(value)
    }.digest()

    assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", ByteString(result).hex())
  }

  @Test fun sha256PaddingSizeValueHashIsCorrect() {
    val result = newMessageDigest("SHA-256").apply {
      update(ByteArray(56) { 'i'.toByte() })
    }.digest()

    assertEquals("aae87235d5b4c89c3b7161d9de513019aa385b8bc2fea4afbff63dd8c1d77ffd", ByteString(result).hex())
  }

  @Test fun sha256ChunkSizeValueHashIsCorrect() {
    val result = newMessageDigest("SHA-256").apply {
      update(ByteArray(64) { 'i'.toByte() })
    }.digest()

    assertEquals("a343b617ce1070a37251a5e66b409947ec3d3ff7d89b9de482d7df84402778d2", ByteString(result).hex())
  }

  @Test fun sha256ValueLargerThanChunkHashIsCorrect() {
    val result = newMessageDigest("SHA-256").apply {
      update(ByteArray(65) { 'i'.toByte() })
    }.digest()

    assertEquals("7db92d77e8b1d5ac593cd614244109b70618fe2a6a7eba541a5347ff383237d0", ByteString(result).hex())
  }

  @Test fun sha256ComplexValueHashIsCorrect() {
    val value = "The quick brown fox jumps over the lazy dog".commonAsUtf8ToByteArray()
    val result = newMessageDigest("SHA-256").apply {
      update(value)
    }.digest()

    assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592", ByteString(result).hex())
  }

  // endregion

  // region SHA-512

  @Test fun sha512EmptyValueHashIsCorrect() {
    val result = newMessageDigest("SHA-512").apply {
      update(byteArrayOf())
    }.digest()
    assertEquals("cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e", ByteString(result).hex())
  }

  @Test fun sha512SimpleValueHashIsCorrect() {
    val result = newMessageDigest("SHA-512").apply {
      update("hello world".commonAsUtf8ToByteArray())
    }.digest()

    assertEquals("309ecc489c12d6eb4cc40f50c902f2b4d0ed77ee511a7c7a9bcd3ca86d4cd86f989dd35bc5ff499670da34255b45b0cfd830e81f605dcf7dc5542e93ae9cd76f", ByteString(result).hex())
  }

  @Test fun sha512PaddingSizeValueHashIsCorrect() {
    val result = newMessageDigest("SHA-512").apply {
      update(ByteArray(112) { 'i'.toByte() })
    }.digest()

    assertEquals("6d5c7c2f505a1767f717a6f9c3787270093c5c331b514413395f9ecb1f9b1fa62514f64e862ba08c3c5a60dde89fdfd9c18e42529399c758c957b78ed9c33221", ByteString(result).hex())
  }

  @Test fun sha512ChunkSizeValueHashIsCorrect() {
    val result = newMessageDigest("SHA-512").apply {
      update(ByteArray(128) { 'i'.toByte() })
    }.digest()

    assertEquals("76b9c0fd4f62bee4541f092d0fd2869fe6f06ca6725be4611c84e27a8641d61adef6020be49c1116284346a8962bed7d5b3df03618cb9273fa5de0b9104a51e8", ByteString(result).hex())
  }

  @Test fun sha512ValueLargerThanChunkHashIsCorrect() {
    val result = newMessageDigest("SHA-512").apply {
      update(ByteArray(129) { 'i'.toByte() })
    }.digest()

    assertEquals("34a15944d86a67fd7b4083502fb5f08b6ae35edd4b4a56d6ae46b45c950a515f79824a933958988dacb42ed71dc30e0d1398d0d9fcf1799d35a3c39aeccbd19f", ByteString(result).hex())
  }

  @Test fun sha512ComplexValueHashIsCorrect() {
    val result = newMessageDigest("SHA-512").apply {
      update("The quick brown fox jumps over the lazy dog".commonAsUtf8ToByteArray())
    }.digest()

    assertEquals("07e547d9586f6a73f73fbac0435ed76951218fb7d0c8d788a309d785436bbb642e93a252a954f23912547d1e8a3b5ed6e1bfd7097821233fa0538f3db854fee6", ByteString(result).hex())
  }

  // endregion

  // region MD5

  @Test fun md5EmptyValueHashIsCorrect() {
    val result = newMessageDigest("MD5").apply {
      update(byteArrayOf())
    }.digest()

    assertEquals("d41d8cd98f00b204e9800998ecf8427e", ByteString(result).hex())
  }

  @Test fun md5SimpleValueHashIsCorrect() {
    val result = newMessageDigest("MD5").apply {
      update("hello world".commonAsUtf8ToByteArray())
    }.digest()

    assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", ByteString(result).hex())
  }

  @Test fun md5PaddingSizeValueIsCorrect() {
    val result = newMessageDigest("MD5").apply {
      update(ByteArray(56) { 'i'.toByte() })
    }.digest()

    assertEquals("b394f36654b7e1a72745e79450c12834", ByteString(result).hex())
  }

  @Test fun md5ChunkSizeValueHashIsCorrect() {
    val result = newMessageDigest("MD5").apply {
      update(ByteArray(64) { 'i'.toByte() })
    }.digest()

    assertEquals("2acbc1a0af1f7d30a2d6d9ecc8e39066", ByteString(result).hex())
  }

  @Test fun md5ValueLargerThanChunkHashIsCorrect() {
    val result = newMessageDigest("MD5").apply {
      update(ByteArray(65) { 'i'.toByte() })
    }.digest()

    assertEquals("0d0a270d6b527f7b876925f07bccb762", ByteString(result).hex())
  }

  @Test fun md5ComplexValueHashIsCorrect() {
    val result = newMessageDigest("MD5").apply {
      update("The quick brown fox jumps over the lazy dog".commonAsUtf8ToByteArray())
    }.digest()

    assertEquals("9e107d9d372bb6826bd81d3542a419d6", ByteString(result).hex())
  }

  // endregion

  private fun OkioMessageDigest.update(byteArray: ByteArray) {
    return update(byteArray, 0, byteArray.size)
  }
}
