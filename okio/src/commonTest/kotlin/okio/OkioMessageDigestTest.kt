package okio

import okio.ByteString.Companion.decodeHex
import kotlin.test.Test

class OkioMessageDigestTest {

  private fun hexToBytes(hex: String) = hex.decodeHex().toByteArray()

  @Test fun `SHA-1 hash of empty value produces correct hash`() {
    val digest = newMessageDigest(OkioMessageDigestAlgorithm.SHA_1)
    val result = digest.digest()
    assertArrayEquals(hexToBytes("da39a3ee5e6b4b0d3255bfef95601890afd80709"), result)
  }
}