package okio

import org.junit.Test
import kotlin.random.Random

class CipherSourceTest {

  @Test
  fun encrypt() {
    val random = Random(1588326457426L)
    val data = random.nextBytes(32)
    val key = random.nextBytes(16)
    val cipherFactory = CipherFactory(key)

    val buffer = Buffer().apply { write(data) }
    val actualEncryptedData =
      buffer.cipherSource(cipherFactory.encrypt).buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun encryptSingleByteSource() {
    val random = Random(1588326457426L)
    val data = random.nextBytes(32)
    val key = random.nextBytes(16)
    val cipherFactory = CipherFactory(key)

    val buffer = Buffer().apply { write(data) }
    val actualEncryptedData =
      buffer.emitSingleBytes().cipherSource(cipherFactory.encrypt).buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun decrypt() {
    val random = Random(1588326610176L)
    val key = random.nextBytes(16)
    val cipherFactory = CipherFactory(key)
    val expectedData = random.nextBytes(32)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val actualData =
      buffer.cipherSource(cipherFactory.decrypt).buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }

  @Test
  fun decryptSingleByteSource() {
    val random = Random(1588326610176L)
    val key = random.nextBytes(16)
    val cipherFactory = CipherFactory(key)
    val expectedData = random.nextBytes(32)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val actualData =
      buffer.emitSingleBytes().cipherSource(cipherFactory.decrypt).buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }
}

private fun BufferedSource.emitSingleBytes(): Source =
  SingleByteSource(this)

private class SingleByteSource(private val source: BufferedSource) : Source {
  override fun read(sink: Buffer, byteCount: Long): Long =
    if (source.exhausted()) {
      -1
    } else {
      sink.writeByte(source.readByte().toInt())
      1
    }

  override fun timeout(): Timeout =
    source.timeout()

  override fun close() {
    source.close()
  }
}
