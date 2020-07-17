package okio.internal

import java.security.MessageDigest

internal actual class Sha1MessageDigest : OkioMessageDigest {

  private val digest = MessageDigest.getInstance("SHA-1")

  override fun update(input: ByteArray) = digest.update(input)

  override fun digest(): ByteArray = digest.digest()
}
