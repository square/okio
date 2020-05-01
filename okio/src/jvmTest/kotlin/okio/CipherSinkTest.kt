package okio

import org.junit.Test
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class CipherSinkTest {

  @Test
  fun encryptWithClose() {
    val random = Random(1588326457426L)
    val data = random.nextBytes(32)
    val iv = random.nextBytes(16)
    val key = random.nextBytes(16)
    val cipherFactory = CipherFactory(iv, key)

    val buffer = Buffer()
    buffer.cipherSink(cipherFactory.encrypt).buffer().use { it.write(data) }
    val actualEncryptedData = buffer.readByteArray()

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun decryptWithClose() {
    val random = Random(1588326610176L)
    val iv = random.nextBytes(16)
    val key = random.nextBytes(16)
    val cipherFactory = CipherFactory(iv, key)
    val expectedData = random.nextBytes(32)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer()
    buffer.cipherSink(cipherFactory.decrypt).buffer().use { it.write(encryptedData) }
    val actualData = buffer.readByteArray()
    actualData[actualData.size - 1] = 0

    assertArrayEquals(expectedData, actualData)
  }
}

private class CipherFactory(
  private val initializationVector: ByteArray,
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
      init(mode, SecretKeySpec(key, keyAlgorithm), IvParameterSpec(initializationVector))
    }
}
