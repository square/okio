package okio

import java.security.MessageDigest

actual fun newMessageDigest(
  algorithm: String
): OkioMessageDigest = object : OkioMessageDigest {

  private val digest: MessageDigest = MessageDigest.getInstance(algorithm)

  override fun update(input: ByteArray) = digest.update(input)

  override fun digest(): ByteArray = digest.digest()
}
