package okio

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.random.Random

@RunWith(Parameterized::class)
class CipherSourceTest(private val cipherAlgorithm: CipherAlgorithm) {
  companion object {
    @get:Parameterized.Parameters(name = "{0}")
    @get:JvmStatic
    val parameters: List<CipherAlgorithm>
      get() = CipherAlgorithm.getBlockCipherAlgorithms()
  }

  @Test
  fun encrypt() {
    val random = Random(787679144228763091)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val data = random.nextBytes(32)

    val buffer = Buffer().apply { write(data) }
    val actualEncryptedData =
      cipherFactory.encrypt.source(buffer).buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun encryptEmpty() {
    val random = Random(1057830944394705953)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val data = ByteArray(0)

    val buffer = Buffer()
    val actualEncryptedData =
      cipherFactory.encrypt.source(buffer).buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun encryptLarge() {
    val random = Random(8185922876836480815)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val data = random.nextBytes(Segment.SIZE * 16 + Segment.SIZE / 2)

    val buffer = Buffer().apply { write(data) }
    val actualEncryptedData =
      cipherFactory.encrypt.source(buffer).buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun encryptSingleByteSource() {
    val random = Random(6085265142433950622)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val data = random.nextBytes(32)

    val buffer = Buffer().apply { write(data) }
    val actualEncryptedData =
      cipherFactory.encrypt.source(buffer.emitSingleBytes()).buffer().use { it.readByteArray() }

    val expectedEncryptedData = cipherFactory.encrypt.doFinal(data)
    assertArrayEquals(expectedEncryptedData, actualEncryptedData)
  }

  @Test
  fun decrypt() {
    val random = Random(8067587635762239433)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val expectedData = random.nextBytes(32)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val actualData =
      cipherFactory.decrypt.source(buffer).buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }

  @Test
  fun decryptEmpty() {
    val random = Random(8722996896871347396)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val expectedData = ByteArray(0)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val actualData =
      cipherFactory.decrypt.source(buffer).buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }

  @Test
  fun decryptLarge() {
    val random = Random(4007116131070653181)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val expectedData = random.nextBytes(Segment.SIZE * 16 + Segment.SIZE / 2)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val actualData =
      cipherFactory.decrypt.source(buffer).buffer().use { it.readByteArray() }

    assertArrayEquals(expectedData, actualData)
  }

  @Test
  fun decryptSingleByteSource() {
    val random = Random(1555017938547616655)
    val cipherFactory = cipherAlgorithm.createCipherFactory(random)
    val expectedData = random.nextBytes(32)
    val encryptedData = cipherFactory.encrypt.doFinal(expectedData)

    val buffer = Buffer().apply { write(encryptedData) }
    val actualData =
      cipherFactory.decrypt.source(buffer.emitSingleBytes()).buffer().use { it.readByteArray() }

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
