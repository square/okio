package okio

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class CipherFactory(
  private val transformation: String,
  private val init: Cipher.(mode: Int) -> Unit
) {
  val blockSize
    get() = cipher.blockSize

  val encrypt: Cipher
    get() = create(Cipher.ENCRYPT_MODE)

  val decrypt: Cipher
    get() = create(Cipher.DECRYPT_MODE)

  private val cipher: Cipher
    get() = Cipher.getInstance(transformation)

  private fun create(mode: Int): Cipher =
    cipher.apply { init(mode) }
}

data class CipherAlgorithm(
  val transformation: String,
  val padding: Boolean,
  val keyLength: Int,
  val ivLength: Int? = null
) {
  fun createCipherFactory(random: Random): CipherFactory {
    val key = random.nextBytes(keyLength)
    val secretKeySpec = SecretKeySpec(key, transformation.substringBefore('/'))
    return if (ivLength == null) {
      CipherFactory(transformation) { mode ->
        init(mode, secretKeySpec)
      }
    } else {
      val iv = random.nextBytes(ivLength)
      val ivParameterSpec = IvParameterSpec(iv)
      CipherFactory(transformation) { mode ->
        init(mode, secretKeySpec, ivParameterSpec)
      }
    }
  }

  override fun toString(): String =
    transformation

  companion object {
    fun getBlockCipherAlgorithms() = listOf(
      CipherAlgorithm("AES/CBC/NoPadding", false, 16, 16),
      CipherAlgorithm("AES/CBC/PKCS5Padding", true, 16, 16),
      CipherAlgorithm("AES/ECB/NoPadding", false, 16),
      CipherAlgorithm("AES/ECB/PKCS5Padding", true, 16),
      CipherAlgorithm("DES/CBC/NoPadding", false, 8, 8),
      CipherAlgorithm("DES/CBC/PKCS5Padding", true, 8, 8),
      CipherAlgorithm("DES/ECB/NoPadding", false, 8),
      CipherAlgorithm("DES/ECB/PKCS5Padding", true, 8),
      CipherAlgorithm("DESede/CBC/NoPadding", false, 24, 8),
      CipherAlgorithm("DESede/CBC/PKCS5Padding", true, 24, 8),
      CipherAlgorithm("DESede/ECB/NoPadding", false, 24),
      CipherAlgorithm("DESede/ECB/PKCS5Padding", true, 24)
    )
  }
}
