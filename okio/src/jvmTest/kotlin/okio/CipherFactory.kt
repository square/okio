package okio

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CipherFactory(
  private val key: ByteArray,
  private val keyAlgorithm: String = "AES",
  private val transformation: String = "AES/ECB/PKCS5Padding"
) {
  val encrypt: Cipher
    get() = create(Cipher.ENCRYPT_MODE)

  val decrypt: Cipher
    get() = create(Cipher.DECRYPT_MODE)

  private fun create(mode: Int): Cipher =
    Cipher.getInstance(transformation).apply {
      init(mode, SecretKeySpec(key, keyAlgorithm))
    }
}
