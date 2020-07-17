package okio

import java.security.MessageDigest

actual fun newMessageDigest(
  algorithm: OkioMessageDigestAlgorithm
): OkioMessageDigest = object : OkioMessageDigest {

  private val digest: MessageDigest = MessageDigest.getInstance(algorithm.value)

  override fun update(input: ByteArray) = digest.update(input)

  override fun digest(): ByteArray = digest.digest()
}
