package okio

import okio.ByteString.Companion.decodeHex
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Utf8TestKt {
  @Test fun onByteCharacters() {
    assertEncoded("00", 0x00) // Smallest 1-byte character.
    assertEncoded("20", ' '.toInt())
    assertEncoded("7e", '~'.toInt())
    assertEncoded("7f", 0x7f) // Largest 1-byte character.
  }

  private fun assertEncoded(hex: String, vararg codePoints: Int) {
    assertCodePointEncoded(hex, *codePoints)
    assertCodePointDecoded(hex, *codePoints)
    assertStringEncoded(hex, String(codePoints, 0, codePoints.size))
  }

  private fun assertCodePointEncoded(hex: String, vararg codePoints: Int) {
    val buffer = Buffer()
    codePoints.forEach { buffer.writeUtf8CodePoint(it) }
    assertEquals(buffer.readByteString(), hex.decodeHex())
  }

  private fun assertCodePointDecoded(hex: String, vararg codePoints: Int) {
    val buffer = Buffer().write(hex.decodeHex())
    codePoints.forEach { assertEquals(it, buffer.readUtf8CodePoint()) }
    assertTrue(buffer.exhausted())
  }

  private fun assertStringEncoded(hex: String, string: String) {
    val expectedUtf8 = hex.decodeHex()

    // Confirm our expectations are consistent with the platform.
    val platformUtf8 = ByteString.of(*string.toByteArray(Charsets.UTF_8))
    assertEquals(expectedUtf8, platformUtf8)

    // Confirm our implementation matches those expectations.
    val actualUtf8 = Buffer().writeUtf8(string).readByteString()
    assertEquals(expectedUtf8, actualUtf8)

    // Confirm we are consistent when writing one code point at a time
    val bufferUtf8 = Buffer()
    var i = 0
    while (i < string.length) {
      val c = string.codePointAt(i)
      bufferUtf8.writeUtf8CodePoint(c)
      i += Character.charCount(c)
    }
    assertEquals(expectedUtf8, bufferUtf8.readByteString())

    // Confirm we are consistent when measuring lengths.
    assertEquals(expectedUtf8.size.toLong(), string.utf8Size())
    assertEquals(expectedUtf8.size.toLong(), string.utf8Size(0, string.length))
  }
}