/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package okio

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.internal.commonAsUtf8ToByteArray
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class CommonByteStringTest {
  class ByteString : AbstractByteStringTest(ByteStringFactory.BYTE_STRING)
  class OkioEncoder : AbstractByteStringTest(ByteStringFactory.OKIO_ENCODER)
}

interface ByteStringFactory {
  // Unused, but useful if/when SegmentedByteString is moved to common
  // This also keeps the unit tests the same between Kotlin/JVM for easy
  // copying as functionality and unit tests are moved
  fun decodeHex(hex: String): ByteString

  fun encodeUtf8(s: String): ByteString

  companion object {
    val BYTE_STRING: ByteStringFactory = object : ByteStringFactory {
      override fun decodeHex(hex: String) = hex.decodeHex()
      override fun encodeUtf8(s: String) = s.encodeUtf8()
    }

    // For Kotlin/JVM, the native Java UTF-8 encoder is used. This forces
    // testing of the Okio encoder used for Kotlin/JS and Kotlin/Native to be
    // tested on JVM as well.
    val OKIO_ENCODER: ByteStringFactory = object : ByteStringFactory {
      override fun decodeHex(hex: String) = hex.decodeHex()
      override fun encodeUtf8(s: String) = ByteString.of(*s.commonAsUtf8ToByteArray())
    }
  }
}

abstract class AbstractByteStringTest(
  private val factory: ByteStringFactory
) {
  @Test fun get() {
    val actual = factory.encodeUtf8("abc")
    assertEquals(3, actual.size)
    assertEquals(actual[0], 'a'.toByte())
    assertEquals(actual[1], 'b'.toByte())
    assertEquals(actual[2], 'c'.toByte())
    try {
      actual[-1]
      fail("no index out of bounds: -1")
    } catch (expected: IndexOutOfBoundsException) {
    }
    try {
      actual[3]
      fail("no index out of bounds: 3")
    } catch (expected: IndexOutOfBoundsException) {
    }
  }

  @Test fun getByte() {
    val byteString = factory.decodeHex("ab12")
    assertEquals(-85, byteString[0].toLong())
    assertEquals(18, byteString[1].toLong())
  }

  @Test fun startsWithByteString() {
    val byteString = factory.decodeHex("112233")
    assertTrue(byteString.startsWith("".decodeHex()))
    assertTrue(byteString.startsWith("11".decodeHex()))
    assertTrue(byteString.startsWith("1122".decodeHex()))
    assertTrue(byteString.startsWith("112233".decodeHex()))
    assertFalse(byteString.startsWith("2233".decodeHex()))
    assertFalse(byteString.startsWith("11223344".decodeHex()))
    assertFalse(byteString.startsWith("112244".decodeHex()))
  }

  @Test fun endsWithByteString() {
    val byteString = factory.decodeHex("112233")
    assertTrue(byteString.endsWith("".decodeHex()))
    assertTrue(byteString.endsWith("33".decodeHex()))
    assertTrue(byteString.endsWith("2233".decodeHex()))
    assertTrue(byteString.endsWith("112233".decodeHex()))
    assertFalse(byteString.endsWith("1122".decodeHex()))
    assertFalse(byteString.endsWith("00112233".decodeHex()))
    assertFalse(byteString.endsWith("002233".decodeHex()))
  }

  @Test fun startsWithByteArray() {
    val byteString = factory.decodeHex("112233")
    assertTrue(byteString.startsWith("".decodeHex().toByteArray()))
    assertTrue(byteString.startsWith("11".decodeHex().toByteArray()))
    assertTrue(byteString.startsWith("1122".decodeHex().toByteArray()))
    assertTrue(byteString.startsWith("112233".decodeHex().toByteArray()))
    assertFalse(byteString.startsWith("2233".decodeHex().toByteArray()))
    assertFalse(byteString.startsWith("11223344".decodeHex().toByteArray()))
    assertFalse(byteString.startsWith("112244".decodeHex().toByteArray()))
  }

  @Test fun endsWithByteArray() {
    val byteString = factory.decodeHex("112233")
    assertTrue(byteString.endsWith("".decodeHex().toByteArray()))
    assertTrue(byteString.endsWith("33".decodeHex().toByteArray()))
    assertTrue(byteString.endsWith("2233".decodeHex().toByteArray()))
    assertTrue(byteString.endsWith("112233".decodeHex().toByteArray()))
    assertFalse(byteString.endsWith("1122".decodeHex().toByteArray()))
    assertFalse(byteString.endsWith("00112233".decodeHex().toByteArray()))
    assertFalse(byteString.endsWith("002233".decodeHex().toByteArray()))
  }

  @Test fun indexOfByteString() {
    val byteString = factory.decodeHex("112233")
    assertEquals(0, byteString.indexOf("112233".decodeHex()).toLong())
    assertEquals(0, byteString.indexOf("1122".decodeHex()).toLong())
    assertEquals(0, byteString.indexOf("11".decodeHex()).toLong())
    assertEquals(0, byteString.indexOf("11".decodeHex(), 0).toLong())
    assertEquals(0, byteString.indexOf("".decodeHex()).toLong())
    assertEquals(0, byteString.indexOf("".decodeHex(), 0).toLong())
    assertEquals(1, byteString.indexOf("2233".decodeHex()).toLong())
    assertEquals(1, byteString.indexOf("22".decodeHex()).toLong())
    assertEquals(1, byteString.indexOf("22".decodeHex(), 1).toLong())
    assertEquals(1, byteString.indexOf("".decodeHex(), 1).toLong())
    assertEquals(2, byteString.indexOf("33".decodeHex()).toLong())
    assertEquals(2, byteString.indexOf("33".decodeHex(), 2).toLong())
    assertEquals(2, byteString.indexOf("".decodeHex(), 2).toLong())
    assertEquals(3, byteString.indexOf("".decodeHex(), 3).toLong())
    assertEquals(-1, byteString.indexOf("112233".decodeHex(), 1).toLong())
    assertEquals(-1, byteString.indexOf("44".decodeHex()).toLong())
    assertEquals(-1, byteString.indexOf("11223344".decodeHex()).toLong())
    assertEquals(-1, byteString.indexOf("112244".decodeHex()).toLong())
    assertEquals(-1, byteString.indexOf("112233".decodeHex(), 1).toLong())
    assertEquals(-1, byteString.indexOf("2233".decodeHex(), 2).toLong())
    assertEquals(-1, byteString.indexOf("33".decodeHex(), 3).toLong())
    assertEquals(-1, byteString.indexOf("".decodeHex(), 4).toLong())
  }

  @Test fun indexOfWithOffset() {
    val byteString = factory.decodeHex("112233112233")
    assertEquals(0, byteString.indexOf("112233".decodeHex(), -1).toLong())
    assertEquals(0, byteString.indexOf("112233".decodeHex(), 0).toLong())
    assertEquals(0, byteString.indexOf("112233".decodeHex()).toLong())
    assertEquals(3, byteString.indexOf("112233".decodeHex(), 1).toLong())
    assertEquals(3, byteString.indexOf("112233".decodeHex(), 2).toLong())
    assertEquals(3, byteString.indexOf("112233".decodeHex(), 3).toLong())
    assertEquals(-1, byteString.indexOf("112233".decodeHex(), 4).toLong())
  }

  @Test fun indexOfByteArray() {
    val byteString = factory.decodeHex("112233")
    assertEquals(0, byteString.indexOf("112233".decodeHex().toByteArray()).toLong())
    assertEquals(1, byteString.indexOf("2233".decodeHex().toByteArray()).toLong())
    assertEquals(2, byteString.indexOf("33".decodeHex().toByteArray()).toLong())
    assertEquals(-1, byteString.indexOf("112244".decodeHex().toByteArray()).toLong())
  }

  @Test fun equalsTest() {
    val byteString = factory.decodeHex("000102")
    assertEquals(byteString, byteString)
    assertEquals(byteString, "000102".decodeHex())
    assertNotEquals(byteString, Any())
    assertNotEquals(byteString, "000201".decodeHex())
  }

  @Ignore // TODO enable when https://youtrack.jetbrains.com/issue/KT-26497 is resolved
  @Test fun equalsEmptyTest() {
    assertEquals(factory.decodeHex(""), ByteString.EMPTY)
    assertEquals(factory.decodeHex(""), ByteString.of())
    assertEquals(ByteString.EMPTY, factory.decodeHex(""))
    assertEquals(ByteString.of(), factory.decodeHex(""))
  }

  private val bronzeHorseman = "На берегу пустынных волн"

  @Test fun utf8() {
    val byteString = factory.encodeUtf8(bronzeHorseman)
    assertEquals(byteString.toByteArray().toList(), bronzeHorseman.commonAsUtf8ToByteArray().toList())
    assertTrue(byteString == ByteString.of(*bronzeHorseman.commonAsUtf8ToByteArray()))
    assertEquals(byteString, ("d09dd0b020d0b1d0b5d180d0b5d0b3d18320d0bfd183d181" +
      "d182d18bd0bdd0bdd18bd18520d0b2d0bed0bbd0bd").decodeHex())
    assertEquals(byteString.utf8(), bronzeHorseman)
  }

  @Test fun testHashCode() {
    val byteString = factory.decodeHex("0102")
    assertEquals(byteString.hashCode().toLong(), byteString.hashCode().toLong())
    assertEquals(byteString.hashCode().toLong(), "0102".decodeHex().hashCode().toLong())
  }

  @Test fun toAsciiLowerCaseNoUppercase() {
    val s = factory.encodeUtf8("a1_+")
    assertEquals(s, s.toAsciiLowercase())
    if (factory === ByteStringFactory.BYTE_STRING) {
      assertSame(s, s.toAsciiLowercase())
    }
  }

  @Test fun toAsciiAllUppercase() {
    assertEquals("ab".encodeUtf8(), factory.encodeUtf8("AB").toAsciiLowercase())
  }

  @Test fun toAsciiStartsLowercaseEndsUppercase() {
    assertEquals("abcd".encodeUtf8(), factory.encodeUtf8("abCD").toAsciiLowercase())
  }

  @Test fun toAsciiStartsUppercaseEndsLowercase() {
    assertEquals("ABCD".encodeUtf8(), factory.encodeUtf8("ABcd").toAsciiUppercase())
  }

  @Test fun encodeBase64() {
    assertEquals("", factory.encodeUtf8("").base64())
    assertEquals("AA==", factory.encodeUtf8("\u0000").base64())
    assertEquals("AAA=", factory.encodeUtf8("\u0000\u0000").base64())
    assertEquals("AAAA", factory.encodeUtf8("\u0000\u0000\u0000").base64())
    assertEquals("SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU/ICdib3V0IDIgbWlsbGlvbi4=",
      factory.encodeUtf8("How many lines of code are there? 'bout 2 million.").base64())
  }

  @Test fun encodeBase64Url() {
    assertEquals("", factory.encodeUtf8("").base64Url())
    assertEquals("AA==", factory.encodeUtf8("\u0000").base64Url())
    assertEquals("AAA=", factory.encodeUtf8("\u0000\u0000").base64Url())
    assertEquals("AAAA", factory.encodeUtf8("\u0000\u0000\u0000").base64Url())
    assertEquals("SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU_ICdib3V0IDIgbWlsbGlvbi4=",
      factory.encodeUtf8("How many lines of code are there? 'bout 2 million.").base64Url())
  }

  @Test fun ignoreUnnecessaryPadding() {
    assertEquals("", "====".decodeBase64()!!.utf8())
    assertEquals("\u0000\u0000\u0000", "AAAA====".decodeBase64()!!.utf8())
  }

  @Test fun decodeBase64() {
    assertEquals("", "".decodeBase64()!!.utf8())
    assertEquals(null, "/===".decodeBase64()) // Can't do anything with 6 bits!
    assertEquals("ff".decodeHex(), "//==".decodeBase64())
    assertEquals("ff".decodeHex(), "__==".decodeBase64())
    assertEquals("ffff".decodeHex(), "///=".decodeBase64())
    assertEquals("ffff".decodeHex(), "___=".decodeBase64())
    assertEquals("ffffff".decodeHex(), "////".decodeBase64())
    assertEquals("ffffff".decodeHex(), "____".decodeBase64())
    assertEquals("ffffffffffff".decodeHex(), "////////".decodeBase64())
    assertEquals("ffffffffffff".decodeHex(), "________".decodeBase64())
    assertEquals("What's to be scared about? It's just a little hiccup in the power...",
      ("V2hhdCdzIHRvIGJlIHNjYXJlZCBhYm91dD8gSXQncyBqdXN0IGEgbGl0dGxlIGhpY2" +
        "N1cCBpbiB0aGUgcG93ZXIuLi4=").decodeBase64()!!.utf8())
    // Uses two encoding styles. Malformed, but supported as a side-effect.
    assertEquals("ffffff".decodeHex(), "__//".decodeBase64())
  }

  @Test fun decodeBase64WithWhitespace() {
    assertEquals("\u0000\u0000\u0000", " AA AA ".decodeBase64()!!.utf8())
    assertEquals("\u0000\u0000\u0000", " AA A\r\nA ".decodeBase64()!!.utf8())
    assertEquals("\u0000\u0000\u0000", "AA AA".decodeBase64()!!.utf8())
    assertEquals("\u0000\u0000\u0000", " AA AA ".decodeBase64()!!.utf8())
    assertEquals("\u0000\u0000\u0000", " AA A\r\nA ".decodeBase64()!!.utf8())
    assertEquals("\u0000\u0000\u0000", "A    AAA".decodeBase64()!!.utf8())
    assertEquals("", "    ".decodeBase64()!!.utf8())
  }

  @Test fun encodeHex() {
    assertEquals("000102", ByteString.of(0x0, 0x1, 0x2).hex())
  }

  @Test fun decodeHex() {
    val actual = "CAFEBABE".decodeHex()
    val expected = ByteString.of(-54, -2, -70, -66)
    assertEquals(expected, actual)
  }

  @Test fun decodeHexOddNumberOfChars() {
    try {
      "aaa".decodeHex()
      fail()
    } catch (expected: IllegalArgumentException) {
    }
  }

  @Test fun decodeHexInvalidChar() {
    try {
      "a\u0000".decodeHex()
      fail()
    } catch (expected: IllegalArgumentException) {
    }
  }

  @Test fun toStringOnEmpty() {
    assertEquals("[size=0]", factory.decodeHex("").toString())
  }

  @Test fun toStringOnShortText() {
    assertEquals("[text=Tyrannosaur]",
      factory.encodeUtf8("Tyrannosaur").toString())
    assertEquals("[text=təˈranəˌsôr]",
      factory.decodeHex("74c999cb8872616ec999cb8c73c3b472").toString())
  }

  @Test fun toStringOnLongTextIsTruncated() {
    val raw = ("Um, I'll tell you the problem with the scientific power that you're using here, " +
      "it didn't require any discipline to attain it. You read what others had done and you " +
      "took the next step. You didn't earn the knowledge for yourselves, so you don't take any " +
      "responsibility for it. You stood on the shoulders of geniuses to accomplish something " +
      "as fast as you could, and before you even knew what you had, you patented it, and " +
      "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna " +
      "sell it.")
    assertEquals("[size=517 text=Um, I'll tell you the problem with the scientific power that " +
      "you…]", factory.encodeUtf8(raw).toString())
    val war = ("Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 𝛄𝓸𝘂'𝒓𝗲 υ𝖘𝓲𝗇ɡ 𝕙𝚎𝑟ｅ, " +
      "𝛊𝓽 ⅆ𝕚𝐝𝝿'𝗍 𝔯𝙚𝙦ᴜ𝜾𝒓𝘦 𝔞𝘯𝐲 ԁ𝜄𝑠𝚌ι𝘱lι𝒏ｅ 𝑡𝜎 𝕒𝚝𝖙𝓪і𝞹 𝔦𝚝. 𝒀ο𝗎 𝔯𝑒⍺𝖉 ｗ𝐡𝝰𝔱 𝞂𝞽һ𝓮𝓇ƽ հ𝖺𝖉 ⅾ𝛐𝝅ⅇ 𝝰πԁ 𝔂ᴑᴜ 𝓉ﮨ၀𝚔 " +
      "т𝒽𝑒 𝗇𝕖ⅹ𝚝 𝔰𝒕е𝓅. 𝘠ⲟ𝖚 𝖉ⅰԁ𝝕'τ 𝙚𝚊ｒ𝞹 𝘵Ꮒ𝖾 𝝒𝐧هｗl𝑒𝖉ƍ𝙚 𝓯૦ｒ 𝔂𝞼𝒖𝕣𝑠𝕖l𝙫𝖊𝓼, 𐑈о ｙ𝘰𝒖 ⅆە𝗇'ｔ 𝜏α𝒌𝕖 𝛂𝟉ℽ " +
      "𝐫ⅇ𝗌ⲣ๐ϖ𝖘ꙇᖯ𝓲l𝓲𝒕𝘆 𝐟𝞼𝘳 𝚤𝑡. 𝛶𝛔𝔲 ｓ𝕥σσ𝐝 ﮩ𝕟 𝒕𝗁𝔢 𝘴𝐡𝜎ᴜlⅾ𝓮𝔯𝚜 𝛐𝙛 ᶃ𝚎ᴨᎥս𝚜𝘦𝓈 𝓽𝞸 ａ𝒄𝚌𝞸ｍρl𝛊ꜱ𝐡 𝓈𝚘ｍ𝚎𝞃𝔥⍳𝞹𝔤 𝐚𝗌 𝖋ａ𝐬𝒕 " +
      "αｓ γ𝛐𝕦 𝔠ﻫ𝛖lԁ, 𝚊π𝑑 Ь𝑒𝙛૦𝓇𝘦 𝓎٥𝖚 ⅇｖℯ𝝅 𝜅ո𝒆ｗ ｗ𝗵𝒂𝘁 ᶌ੦𝗎 ｈ𝐚𝗱, 𝜸ﮨ𝒖 𝓹𝝰𝔱𝖾𝗇𝓽𝔢ⅆ і𝕥, 𝚊𝜛𝓭 𝓹𝖺ⅽϰ𝘢ℊеᏧ 𝑖𝞃, " +
      "𝐚𝛑ꓒ 𝙨l𝔞р𝘱𝔢𝓭 ɩ𝗍 ہ𝛑 𝕒 ｐl𝛂ѕᴛ𝗂𝐜 l𝞄ℼ𝔠𝒽𝑏ﮪ⨯, 𝔞ϖ𝒹 ｎ𝛔ｗ 𝛾𝐨𝞄'𝗿𝔢 ꜱ℮ll𝙞ｎɡ ɩ𝘁, 𝙮𝕠𝛖 ｗ𝑎ℼ𝚗𝛂 𝕤𝓮ll 𝙞𝓉.")
    assertEquals("[size=1496 text=Սｍ, I'll 𝓽𝖾ll ᶌօ𝘂 ᴛℎ℮ 𝜚𝕣०ｂl𝖾ｍ ｗі𝕥𝒽 𝘵𝘩𝐞 𝓼𝙘𝐢𝔢𝓷𝗍𝜄𝚏𝑖ｃ 𝛠𝝾ｗ𝚎𝑟 𝕥ｈ⍺𝞃 " +
      "𝛄𝓸𝘂…]", factory.encodeUtf8(war).toString())
  }

  @Test fun toStringOnTextWithNewlines() {
    // Instead of emitting a literal newline in the toString(), these are escaped as "\n".
    assertEquals("[text=a\\r\\nb\\nc\\rd\\\\e]",
      factory.encodeUtf8("a\r\nb\nc\rd\\e").toString())
  }

  @Test fun toStringOnData() {
    val byteString = factory.decodeHex("" +
      "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
      "4bf0b54023c29b624de9ef9c2f931efc580f9afb")
    assertEquals("[hex=" +
      "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
      "4bf0b54023c29b624de9ef9c2f931efc580f9afb]", byteString.toString())
  }

  @Test fun toStringOnLongDataIsTruncated() {
    val byteString = factory.decodeHex("" +
      "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
      "4bf0b54023c29b624de9ef9c2f931efc580f9afba1")
    assertEquals("[size=65 hex=" +
      "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55" +
      "4bf0b54023c29b624de9ef9c2f931efc580f9afb…]", byteString.toString())
  }

  @Test fun compareToSingleBytes() {
    val originalByteStrings = listOf(
      factory.decodeHex("00"),
      factory.decodeHex("01"),
      factory.decodeHex("7e"),
      factory.decodeHex("7f"),
      factory.decodeHex("80"),
      factory.decodeHex("81"),
      factory.decodeHex("fe"),
      factory.decodeHex("ff"))

    val sortedByteStrings = originalByteStrings.toMutableList()
    sortedByteStrings.shuffle() // TODO Constant random for repeatability
    sortedByteStrings.sort()

    assertEquals(originalByteStrings, sortedByteStrings)
  }

  @Test fun compareToMultipleBytes() {
    val originalByteStrings = listOf(
      factory.decodeHex(""),
      factory.decodeHex("00"),
      factory.decodeHex("0000"),
      factory.decodeHex("000000"),
      factory.decodeHex("00000000"),
      factory.decodeHex("0000000000"),
      factory.decodeHex("0000000001"),
      factory.decodeHex("000001"),
      factory.decodeHex("00007f"),
      factory.decodeHex("0000ff"),
      factory.decodeHex("000100"),
      factory.decodeHex("000101"),
      factory.decodeHex("007f00"),
      factory.decodeHex("00ff00"),
      factory.decodeHex("010000"),
      factory.decodeHex("010001"),
      factory.decodeHex("01007f"),
      factory.decodeHex("0100ff"),
      factory.decodeHex("010100"),
      factory.decodeHex("01010000"),
      factory.decodeHex("0101000000"),
      factory.decodeHex("0101000001"),
      factory.decodeHex("010101"),
      factory.decodeHex("7f0000"),
      factory.decodeHex("7f0000ffff"),
      factory.decodeHex("ffffff"))

    val sortedByteStrings = originalByteStrings.toMutableList()
    sortedByteStrings.shuffle() // TODO Constant random for repeatability
    sortedByteStrings.sort()

    assertEquals(originalByteStrings, sortedByteStrings)
  }
}
