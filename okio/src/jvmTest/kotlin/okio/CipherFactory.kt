package okio

import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class CipherFactory(
  private val key: Key,
  private val transformation: String = "AES/ECB/PKCS5Padding"
) {

  constructor(
    key: ByteArray,
    keyAlgorithm: String = "AES",
    transformation: String = "AES/ECB/PKCS5Padding"
  ) : this(SecretKeySpec(key, keyAlgorithm), transformation)

  val encrypt: Cipher
    get() = create(Cipher.ENCRYPT_MODE)

  val decrypt: Cipher
    get() = create(Cipher.DECRYPT_MODE)

  private fun create(mode: Int): Cipher =
    Cipher.getInstance(transformation).apply {
      init(mode, key)
    }
}
