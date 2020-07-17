package okio

import okio.ByteString.Companion.decodeHex
import okio.internal.commonAsUtf8ToByteArray
import kotlin.test.Test

class OkioMessageDigestTest {

  private fun hexToBytes(hex: String) = hex.decodeHex().toByteArray()

  // region SHA-1

  @Test fun `SHA-1 hash of empty value produces correct hash`() {
    // arrange
    val digest = newMessageDigest(OkioMessageDigestAlgorithm.SHA_1)

    // act
    val result = digest.digest()

    // assert
    assertArrayEquals(hexToBytes("da39a3ee5e6b4b0d3255bfef95601890afd80709"), result)
  }

  @Test fun `SHA-1 hash of simple value gives correct hash`() {
    // arrange
    val digest = newMessageDigest(OkioMessageDigestAlgorithm.SHA_1)

    // act
    val result = digest.apply {
      update("Kevin".commonAsUtf8ToByteArray())
    }.digest()

    // assert
    assertArrayEquals(hexToBytes("e043899daa0c7add37bc99792b2c045d6abbc6dc"), result)
  }

  @Test fun `SHA-1 hash of exact chunk size value gives correct hash`() {
    // arrange
    val digest = newMessageDigest(OkioMessageDigestAlgorithm.SHA_1)

    // act
    val result = digest.apply {
      for (i in 0 until 64) update("i".commonAsUtf8ToByteArray())
    }.digest()

    // assert
    assertArrayEquals(hexToBytes("79c64455d4565a82bc3f4ec5d9a5e8443c2e77b3"), result)
  }

  @Test fun `SHA-1 hash of value larger than chunk size gives correct hash`() {
    // arrange
    val digest = newMessageDigest(OkioMessageDigestAlgorithm.SHA_1)

    // act
    val result = digest.apply {
      for (i in 0 until 65) update("i".commonAsUtf8ToByteArray())
    }.digest()

    // assert
    assertArrayEquals(hexToBytes("6658b01c97fd6db0bdb010b2e154164285e5bd71"), result)
  }

  @Test fun `SHA-1 hash of non-trivial value gives correct digest`() {
    // arrange
    val digest = newMessageDigest(OkioMessageDigestAlgorithm.SHA_1)

    // act
    val result = digest.apply {
      update("The quick brown fox jumps over the lazy dog".commonAsUtf8ToByteArray())
    }.digest()

    // assert
    assertArrayEquals(hexToBytes("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12"), result)
  }

  // endregion

  // region SHA-256

  // endregion

  // region SHA-512

  // endregion

  // region MD5

  // endregion
}