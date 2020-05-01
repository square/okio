package okio

import org.junit.Test
import kotlin.random.Random

class CipherSourceTest {

  @Test
  fun encryptWithClose() {
    val random = Random(1588326457426L)
    val data = random.nextBytes(32)
    val key = random.nextBytes(16)
    val cipherFactory = CipherFactory(key)

    val buffer = Buffer().apply { write(data) }
    val actualEncryptedData = buffer.cipherSource(cipherFactory.encrypt).buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun decryptWithClose() {
    val random = Random(1588326610176L)
    val key = random.nextBytes(16)
    val cipherFactory = CipherFactory(key)
    val expectedData = random.nextBytes(32)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val actualData = buffer.cipherSource(cipherFactory.decrypt).buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }
}
