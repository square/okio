package okio

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
class BufferBase64Test(val size: Int) {

  private val random = Random(4975347L + size)
  private val bytes = random.nextBytes(size)

  companion object {
    @get:Parameterized.Parameters(name = "{0}")
    @get:JvmStatic
    val parameters: List<Int>
      get() = (0..32).toList() + ((Segment.SIZE - 32)..Segment.SIZE + 32).toList()

    private val base64Encoder = Base64.getEncoder()
    private val base64UrlEncoder = Base64.getUrlEncoder()

    private fun Random.nextBytes(size: Int): ByteArray =
      ByteArray(size).also { nextBytes(it) }
  }

  @Test
  fun write() {
    val encoded = base64Encoder.encodeToString(bytes)

    val buffer = Buffer().apply { writeBase64(encoded) }

    val byteArray = buffer.readByteArray()
    assertArrayEquals(bytes, byteArray)
  }

  @Test
  fun writeWithWhitespace() {
    val encoded = base64Encoder.encodeToString(bytes).chunked(8).joinToString("\n")

    val buffer = Buffer().apply { writeBase64(encoded) }

    val byteArray = buffer.readByteArray()
    assertArrayEquals(bytes, byteArray)
  }

  @Test
  fun writeCorruptedInvalidChar() {
    if (size == 0) return // Skip this test when there is no data
    val corruptedIndex = random.nextInt(size)
    val encoded =
      base64Encoder.encodeToString(bytes).replaceRange(corruptedIndex..corruptedIndex, "?")

    val buffer = Buffer()
    assertFailsWith<IllegalArgumentException> {
      buffer.writeBase64(encoded)
    }
  }

  @Test
  fun writeCorruptedInvalidLength() {
    if (size == 0) return // Skip this test when there is no data
    val encoded = base64Encoder.encodeToString(bytes) + "A"

    val buffer = Buffer()
    assertFailsWith<IllegalArgumentException> {
      buffer.writeBase64(encoded)
    }
  }

  @Test
  fun writeMultiple() {
    val buffer = Buffer().apply {
      bytes.asList().chunked(4).forEach {
        val encoded = base64Encoder.encodeToString(it.toByteArray())
        writeBase64(encoded)
      }
    }

    val byteArray = buffer.readByteArray()
    assertArrayEquals(bytes, byteArray)
  }

  @Test
  fun writeUrlEncoded() {
    val encoded = base64UrlEncoder.encodeToString(bytes)

    val buffer = Buffer().apply { writeBase64(encoded) }

    val byteArray = buffer.readByteArray()
    assertArrayEquals(bytes, byteArray)
  }

  @Test
  fun read() {
    val buffer = Buffer().apply { write(bytes) }

    val s = buffer.readBase64()

    assertEquals(base64Encoder.encodeToString(bytes), s)
  }

  @Test
  fun readUrlEncoded() {
    val buffer = Buffer().apply { write(bytes) }

    val s = buffer.readBase64Url()

    val encoded = base64UrlEncoder.encodeToString(bytes)
    assertEquals(encoded, s)
  }

  @Test
  fun readFragmented() {
    // Buffer made of segments with only one byte, randomly located
    val buffer = Buffer().apply {
      bytes.forEach {
        val s = writableSegment(Segment.SIZE)
        check(s.pos == 0 && s.limit == 0) // Implementation should provide an empty segment
        val pos = random.nextInt(Segment.SIZE)
        s.pos = pos
        s.data[pos] = it
        s.limit = pos + 1
        size++
      }
    }

    val s = buffer.readBase64()

    val encoded = base64Encoder.encodeToString(bytes)
    assertEquals(encoded, s)
  }
}
