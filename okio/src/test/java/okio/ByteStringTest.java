/*
 * Copyright 2014 Square Inc.
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
package okio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static okio.TestUtil.assertByteArraysEquals;
import static okio.TestUtil.assertEquivalent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public final class ByteStringTest {
  interface Factory {
    Factory BYTE_STRING = new Factory() {
      @Override public ByteString decodeHex(String hex) {
        return ByteString.decodeHex(hex);
      }

      @Override public ByteString encodeUtf8(String s) {
        return ByteString.encodeUtf8(s);
      }
    };

    Factory SEGMENTED_BYTE_STRING = new Factory() {
      @Override public ByteString decodeHex(String hex) {
        Buffer buffer = new Buffer();
        buffer.write(ByteString.decodeHex(hex));
        return buffer.snapshot();
      }

      @Override public ByteString encodeUtf8(String s) {
        Buffer buffer = new Buffer();
        buffer.writeUtf8(s);
        return buffer.snapshot();
      }
    };

    Factory ONE_BYTE_PER_SEGMENT = new Factory() {
      @Override public ByteString decodeHex(String hex) {
        return makeSegments(ByteString.decodeHex(hex));
      }

      @Override public ByteString encodeUtf8(String s) {
        return makeSegments(ByteString.encodeUtf8(s));
      }

      private ByteString makeSegments(ByteString source) {
        Buffer buffer = new Buffer();
        for (int i = 0; i < source.size(); i++) {
          Segment segment = buffer.writableSegment(Segment.SIZE);
          segment.data[segment.pos] = source.getByte(i);
          segment.limit++;
          buffer.size++;
        }
        return buffer.snapshot();
      }
    };

    ByteString decodeHex(String hex);
    ByteString encodeUtf8(String s);
  }

  @Parameters(name = "{1}")
  public static List<Object[]> parameters() {
    return Arrays.asList(
        new Object[] { Factory.BYTE_STRING, "ByteString" },
        new Object[] { Factory.SEGMENTED_BYTE_STRING, "SegmentedByteString" },
        new Object[] { Factory.ONE_BYTE_PER_SEGMENT, "SegmentedByteString (one-at-a-time)" });
  }

  @Parameter(0) public Factory factory;
  @Parameter(1) public String name;

  @Test public void ofCopyRange() {
    byte[] bytes = "Hello, World!".getBytes(Util.UTF_8);
    ByteString byteString = ByteString.of(bytes, 2, 9);
    // Verify that the bytes were copied out.
    bytes[4] = (byte) 'a';
    assertEquals("llo, Worl", byteString.utf8());
  }

  @Test public void ofByteBuffer() {
    byte[] bytes = "Hello, World!".getBytes(Util.UTF_8);
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
    byteBuffer.position(2).limit(11);
    ByteString byteString = ByteString.of(byteBuffer);
    // Verify that the bytes were copied out.
    byteBuffer.put(4, (byte) 'a');
    assertEquals("llo, Worl", byteString.utf8());
  }

  @Test public void getByte() throws Exception {
    ByteString byteString = factory.decodeHex("ab12");
    assertEquals(-85, byteString.getByte(0));
    assertEquals(18, byteString.getByte(1));
  }

  @Test public void getByteOutOfBounds() throws Exception {
    ByteString byteString = factory.decodeHex("ab12");
    try {
      byteString.getByte(2);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  @Test public void startsWithByteString() throws Exception {
    ByteString byteString = factory.decodeHex("112233");
    assertTrue(byteString.startsWith(ByteString.decodeHex("")));
    assertTrue(byteString.startsWith(ByteString.decodeHex("11")));
    assertTrue(byteString.startsWith(ByteString.decodeHex("1122")));
    assertTrue(byteString.startsWith(ByteString.decodeHex("112233")));
    assertFalse(byteString.startsWith(ByteString.decodeHex("2233")));
    assertFalse(byteString.startsWith(ByteString.decodeHex("11223344")));
    assertFalse(byteString.startsWith(ByteString.decodeHex("112244")));
  }

  @Test public void endsWithByteString() throws Exception {
    ByteString byteString = factory.decodeHex("112233");
    assertTrue(byteString.endsWith(ByteString.decodeHex("")));
    assertTrue(byteString.endsWith(ByteString.decodeHex("33")));
    assertTrue(byteString.endsWith(ByteString.decodeHex("2233")));
    assertTrue(byteString.endsWith(ByteString.decodeHex("112233")));
    assertFalse(byteString.endsWith(ByteString.decodeHex("1122")));
    assertFalse(byteString.endsWith(ByteString.decodeHex("00112233")));
    assertFalse(byteString.endsWith(ByteString.decodeHex("002233")));
  }

  @Test public void startsWithByteArray() throws Exception {
    ByteString byteString = factory.decodeHex("112233");
    assertTrue(byteString.startsWith(ByteString.decodeHex("").toByteArray()));
    assertTrue(byteString.startsWith(ByteString.decodeHex("11").toByteArray()));
    assertTrue(byteString.startsWith(ByteString.decodeHex("1122").toByteArray()));
    assertTrue(byteString.startsWith(ByteString.decodeHex("112233").toByteArray()));
    assertFalse(byteString.startsWith(ByteString.decodeHex("2233").toByteArray()));
    assertFalse(byteString.startsWith(ByteString.decodeHex("11223344").toByteArray()));
    assertFalse(byteString.startsWith(ByteString.decodeHex("112244").toByteArray()));
  }

  @Test public void endsWithByteArray() throws Exception {
    ByteString byteString = factory.decodeHex("112233");
    assertTrue(byteString.endsWith(ByteString.decodeHex("").toByteArray()));
    assertTrue(byteString.endsWith(ByteString.decodeHex("33").toByteArray()));
    assertTrue(byteString.endsWith(ByteString.decodeHex("2233").toByteArray()));
    assertTrue(byteString.endsWith(ByteString.decodeHex("112233").toByteArray()));
    assertFalse(byteString.endsWith(ByteString.decodeHex("1122").toByteArray()));
    assertFalse(byteString.endsWith(ByteString.decodeHex("00112233").toByteArray()));
    assertFalse(byteString.endsWith(ByteString.decodeHex("002233").toByteArray()));
  }

  @Test public void indexOfByteString() throws Exception {
    ByteString byteString = factory.decodeHex("112233");
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233")));
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("1122")));
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("11")));
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("11"), 0));
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("")));
    assertEquals(0, byteString.indexOf(ByteString.decodeHex(""), 0));
    assertEquals(1, byteString.indexOf(ByteString.decodeHex("2233")));
    assertEquals(1, byteString.indexOf(ByteString.decodeHex("22")));
    assertEquals(1, byteString.indexOf(ByteString.decodeHex("22"), 1));
    assertEquals(1, byteString.indexOf(ByteString.decodeHex(""), 1));
    assertEquals(2, byteString.indexOf(ByteString.decodeHex("33")));
    assertEquals(2, byteString.indexOf(ByteString.decodeHex("33"), 2));
    assertEquals(2, byteString.indexOf(ByteString.decodeHex(""), 2));
    assertEquals(3, byteString.indexOf(ByteString.decodeHex(""), 3));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 1));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("44")));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("11223344")));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112244")));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 1));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("2233"), 2));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("33"), 3));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex(""), 4));
  }

  @Test public void indexOfWithOffset() throws Exception {
    ByteString byteString = factory.decodeHex("112233112233");
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233"), -1));
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233"), 0));
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233")));
    assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 1));
    assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 2));
    assertEquals(3, byteString.indexOf(ByteString.decodeHex("112233"), 3));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112233"), 4));
  }

  @Test public void indexOfByteArray() throws Exception {
    ByteString byteString = factory.decodeHex("112233");
    assertEquals(0, byteString.indexOf(ByteString.decodeHex("112233").toByteArray()));
    assertEquals(1, byteString.indexOf(ByteString.decodeHex("2233").toByteArray()));
    assertEquals(2, byteString.indexOf(ByteString.decodeHex("33").toByteArray()));
    assertEquals(-1, byteString.indexOf(ByteString.decodeHex("112244").toByteArray()));
  }

  @Test public void lastIndexOfByteString() throws Exception {
    ByteString byteString = factory.decodeHex("112233");
    assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("112233")));
    assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("1122")));
    assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11")));
    assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11"), 3));
    assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("11"), 0));
    assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex(""), 0));
    assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("2233")));
    assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22")));
    assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22"), 3));
    assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("22"), 1));
    assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex(""), 1));
    assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33")));
    assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33"), 3));
    assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33"), 2));
    assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex(""), 2));
    assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex(""), 3));
    assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex("")));
    assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112233"), -1));
    assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112233"), -2));
    assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("44")));
    assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("11223344")));
    assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("112244")));
    assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("2233"), 0));
    assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex("33"), 1));
    assertEquals(-1, byteString.lastIndexOf(ByteString.decodeHex(""), -1));
  }

  @Test public void lastIndexOfByteArray() throws Exception {
    ByteString byteString = factory.decodeHex("112233");
    assertEquals(0, byteString.lastIndexOf(ByteString.decodeHex("112233").toByteArray()));
    assertEquals(1, byteString.lastIndexOf(ByteString.decodeHex("2233").toByteArray()));
    assertEquals(2, byteString.lastIndexOf(ByteString.decodeHex("33").toByteArray()));
    assertEquals(3, byteString.lastIndexOf(ByteString.decodeHex("").toByteArray()));
  }

  @Test public void equals() throws Exception {
    ByteString byteString = factory.decodeHex("000102");
    assertTrue(byteString.equals(byteString));
    assertTrue(byteString.equals(ByteString.decodeHex("000102")));
    assertTrue(factory.decodeHex("").equals(ByteString.EMPTY));
    assertTrue(factory.decodeHex("").equals(ByteString.of()));
    assertTrue(ByteString.EMPTY.equals(factory.decodeHex("")));
    assertTrue(ByteString.of().equals(factory.decodeHex("")));
    assertFalse(byteString.equals(new Object()));
    assertFalse(byteString.equals(ByteString.decodeHex("000201")));
  }

  private final String bronzeHorseman = "На берегу пустынных волн";

  @Test public void utf8() throws Exception {
    ByteString byteString = factory.encodeUtf8(bronzeHorseman);
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(Util.UTF_8));
    assertTrue(byteString.equals(ByteString.of(bronzeHorseman.getBytes(Util.UTF_8))));
    assertEquals(byteString.utf8(), bronzeHorseman);
  }

  @Test public void encodeNullCharset() throws Exception {
    try {
      ByteString.encodeString("hello", null);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void encodeNullString() throws Exception {
    try {
      ByteString.encodeString(null, Charset.forName("UTF-8"));
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void decodeNullCharset() throws Exception {
    try {
      ByteString.of().string(null);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void encodeDecodeStringUtf8() throws Exception {
    Charset utf8 = Charset.forName("UTF-8");
    ByteString byteString = ByteString.encodeString(bronzeHorseman, utf8);
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(utf8));
    assertEquals(byteString, ByteString.decodeHex("d09dd0b020d0b1d0b5d180d0b5d0b3d18320d0bfd183d181"
        + "d182d18bd0bdd0bdd18bd18520d0b2d0bed0bbd0bd"));
    assertEquals(bronzeHorseman, byteString.string(utf8));
  }

  @Test public void encodeDecodeStringUtf16be() throws Exception {
    Charset utf16be = Charset.forName("UTF-16BE");
    ByteString byteString = ByteString.encodeString(bronzeHorseman, utf16be);
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(utf16be));
    assertEquals(byteString, ByteString.decodeHex("041d043000200431043504400435043304430020043f0443"
        + "04410442044b043d043d044b044500200432043e043b043d"));
    assertEquals(bronzeHorseman, byteString.string(utf16be));
  }

  @Test public void encodeDecodeStringUtf32be() throws Exception {
    Charset utf32be = Charset.forName("UTF-32BE");
    ByteString byteString = ByteString.encodeString(bronzeHorseman, utf32be);
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(utf32be));
    assertEquals(byteString, ByteString.decodeHex("0000041d0000043000000020000004310000043500000440"
        + "000004350000043300000443000000200000043f0000044300000441000004420000044b0000043d0000043d"
        + "0000044b0000044500000020000004320000043e0000043b0000043d"));
    assertEquals(bronzeHorseman, byteString.string(utf32be));
  }

  @Test public void encodeDecodeStringAsciiIsLossy() throws Exception {
    Charset ascii = Charset.forName("US-ASCII");
    ByteString byteString = ByteString.encodeString(bronzeHorseman, ascii);
    assertByteArraysEquals(byteString.toByteArray(), bronzeHorseman.getBytes(ascii));
    assertEquals(byteString,
        ByteString.decodeHex("3f3f203f3f3f3f3f3f203f3f3f3f3f3f3f3f3f203f3f3f3f"));
    assertEquals("?? ?????? ????????? ????", byteString.string(ascii));
  }

  @Test public void decodeMalformedStringReturnsReplacementCharacter() throws Exception {
    Charset utf16be = Charset.forName("UTF-16BE");
    String string = ByteString.decodeHex("04").string(utf16be);
    assertEquals("\ufffd", string);
  }

  @Test public void testHashCode() throws Exception {
    ByteString byteString = factory.decodeHex("0102");
    assertEquals(byteString.hashCode(), byteString.hashCode());
    assertEquals(byteString.hashCode(), ByteString.decodeHex("0102").hashCode());
  }

  @Test public void read() throws Exception {
    InputStream in = new ByteArrayInputStream("abc".getBytes(Util.UTF_8));
    assertEquals(ByteString.decodeHex("6162"), ByteString.read(in, 2));
    assertEquals(ByteString.decodeHex("63"), ByteString.read(in, 1));
    assertEquals(ByteString.of(), ByteString.read(in, 0));
  }

  @Test public void readAndToLowercase() throws Exception {
    InputStream in = new ByteArrayInputStream("ABC".getBytes(Util.UTF_8));
    assertEquals(ByteString.encodeUtf8("ab"), ByteString.read(in, 2).toAsciiLowercase());
    assertEquals(ByteString.encodeUtf8("c"), ByteString.read(in, 1).toAsciiLowercase());
    assertEquals(ByteString.EMPTY, ByteString.read(in, 0).toAsciiLowercase());
  }

  @Test public void toAsciiLowerCaseNoUppercase() throws Exception {
    ByteString s = factory.encodeUtf8("a1_+");
    assertEquals(s, s.toAsciiLowercase());
    if (factory == Factory.BYTE_STRING) {
      assertSame(s, s.toAsciiLowercase());
    }
  }

  @Test public void toAsciiAllUppercase() throws Exception {
    assertEquals(ByteString.encodeUtf8("ab"), factory.encodeUtf8("AB").toAsciiLowercase());
  }

  @Test public void toAsciiStartsLowercaseEndsUppercase() throws Exception {
    assertEquals(ByteString.encodeUtf8("abcd"), factory.encodeUtf8("abCD").toAsciiLowercase());
  }

  @Test public void readAndToUppercase() throws Exception {
    InputStream in = new ByteArrayInputStream("abc".getBytes(Util.UTF_8));
    assertEquals(ByteString.encodeUtf8("AB"), ByteString.read(in, 2).toAsciiUppercase());
    assertEquals(ByteString.encodeUtf8("C"), ByteString.read(in, 1).toAsciiUppercase());
    assertEquals(ByteString.EMPTY, ByteString.read(in, 0).toAsciiUppercase());
  }

  @Test public void toAsciiStartsUppercaseEndsLowercase() throws Exception {
    assertEquals(ByteString.encodeUtf8("ABCD"), factory.encodeUtf8("ABcd").toAsciiUppercase());
  }

  @Test public void substring() throws Exception {
    ByteString byteString = factory.encodeUtf8("Hello, World!");

    assertEquals(byteString.substring(0), byteString);
    assertEquals(byteString.substring(0, 5), ByteString.encodeUtf8("Hello"));
    assertEquals(byteString.substring(7), ByteString.encodeUtf8("World!"));
    assertEquals(byteString.substring(6, 6), ByteString.encodeUtf8(""));
  }

  @Test public void substringWithInvalidBounds() throws Exception {
    ByteString byteString = factory.encodeUtf8("Hello, World!");

    try {
      byteString.substring(-1);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      byteString.substring(0, 14);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      byteString.substring(8, 7);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void write() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    factory.decodeHex("616263").write(out);
    assertByteArraysEquals(new byte[] { 0x61, 0x62, 0x63 }, out.toByteArray());
  }

  @Test public void encodeBase64() {
    assertEquals("", factory.encodeUtf8("").base64());
    assertEquals("AA==", factory.encodeUtf8("\u0000").base64());
    assertEquals("AAA=", factory.encodeUtf8("\u0000\u0000").base64());
    assertEquals("AAAA", factory.encodeUtf8("\u0000\u0000\u0000").base64());
    assertEquals("SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU/ICdib3V0IDIgbWlsbGlvbi4=",
        factory.encodeUtf8("How many lines of code are there? 'bout 2 million.").base64());
  }

  @Test public void encodeBase64Url() {
    assertEquals("", factory.encodeUtf8("").base64Url());
    assertEquals("AA==", factory.encodeUtf8("\u0000").base64Url());
    assertEquals("AAA=", factory.encodeUtf8("\u0000\u0000").base64Url());
    assertEquals("AAAA", factory.encodeUtf8("\u0000\u0000\u0000").base64Url());
    assertEquals("SG93IG1hbnkgbGluZXMgb2YgY29kZSBhcmUgdGhlcmU_ICdib3V0IDIgbWlsbGlvbi4=",
        factory.encodeUtf8("How many lines of code are there? 'bout 2 million.").base64Url());
  }

  @Test public void ignoreUnnecessaryPadding() {
    assertEquals("", ByteString.decodeBase64("====").utf8());
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("AAAA====").utf8());
  }

  @Test public void decodeBase64() {
    assertEquals("", ByteString.decodeBase64("").utf8());
    assertEquals(null, ByteString.decodeBase64("/===")); // Can't do anything with 6 bits!
    assertEquals(ByteString.decodeHex("ff"), ByteString.decodeBase64("//=="));
    assertEquals(ByteString.decodeHex("ff"), ByteString.decodeBase64("__=="));
    assertEquals(ByteString.decodeHex("ffff"), ByteString.decodeBase64("///="));
    assertEquals(ByteString.decodeHex("ffff"), ByteString.decodeBase64("___="));
    assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("////"));
    assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("____"));
    assertEquals(ByteString.decodeHex("ffffffffffff"), ByteString.decodeBase64("////////"));
    assertEquals(ByteString.decodeHex("ffffffffffff"), ByteString.decodeBase64("________"));
    assertEquals("What's to be scared about? It's just a little hiccup in the power...",
        ByteString.decodeBase64("V2hhdCdzIHRvIGJlIHNjYXJlZCBhYm91dD8gSXQncyBqdXN0IGEgbGl0dGxlIGhpY2"
            + "N1cCBpbiB0aGUgcG93ZXIuLi4=").utf8());
    // Uses two encoding styles. Malformed, but supported as a side-effect.
    assertEquals(ByteString.decodeHex("ffffff"), ByteString.decodeBase64("__//"));
  }

  @Test public void decodeBase64WithWhitespace() {
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA AA ").utf8());
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA A\r\nA ").utf8());
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("AA AA").utf8());
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA AA ").utf8());
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64(" AA A\r\nA ").utf8());
    assertEquals("\u0000\u0000\u0000", ByteString.decodeBase64("A    AAA").utf8());
    assertEquals("", ByteString.decodeBase64("    ").utf8());
  }

  @Test public void encodeHex() throws Exception {
    assertEquals("000102", ByteString.of((byte) 0x0, (byte) 0x1, (byte) 0x2).hex());
  }

  @Test public void decodeHex() throws Exception {
    assertEquals(ByteString.of((byte) 0x0, (byte) 0x1, (byte) 0x2), ByteString.decodeHex("000102"));
  }

  @Test public void decodeHexOddNumberOfChars() throws Exception {
    try {
      ByteString.decodeHex("aaa");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void decodeHexInvalidChar() throws Exception {
    try {
      ByteString.decodeHex("a\u0000");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void toStringOnEmpty() {
    assertEquals("[size=0]", factory.decodeHex("").toString());
  }

  @Test public void toStringOnShortText() {
    assertEquals("[text=Tyrannosaur]",
        factory.encodeUtf8("Tyrannosaur").toString());
    assertEquals("[text=təˈranəˌsôr]",
        factory.decodeHex("74c999cb8872616ec999cb8c73c3b472").toString());
  }

  @Test public void toStringOnLongTextIsTruncated() {
    String raw = "Um, I'll tell you the problem with the scientific power that you're using here, "
        + "it didn't require any discipline to attain it. You read what others had done and you "
        + "took the next step. You didn't earn the knowledge for yourselves, so you don't take any "
        + "responsibility for it. You stood on the shoulders of geniuses to accomplish something "
        + "as fast as you could, and before you even knew what you had, you patented it, and "
        + "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna "
        + "sell it.";
    assertEquals("[size=517 text=Um, I'll tell you the problem with the scientific power that "
        + "you…]", factory.encodeUtf8(raw).toString());
  }

  @Test public void toStringOnTextWithNewlines() {
    // Instead of emitting a literal newline in the toString(), these are escaped as "\n".
    assertEquals("[text=a\\r\\nb\\nc\\rd\\\\e]",
        factory.encodeUtf8("a\r\nb\nc\rd\\e").toString());
  }

  @Test public void toStringOnData() {
    ByteString byteString = factory.decodeHex(""
        + "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55"
        + "4bf0b54023c29b624de9ef9c2f931efc580f9afb");
    assertEquals("[hex="
        + "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55"
        + "4bf0b54023c29b624de9ef9c2f931efc580f9afb]", byteString.toString());
  }

  @Test public void toStringOnLongDataIsTruncated() {
    ByteString byteString = factory.decodeHex(""
        + "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55"
        + "4bf0b54023c29b624de9ef9c2f931efc580f9afba1");
    assertEquals("[size=65 hex="
        + "60b420bb3851d9d47acb933dbe70399bf6c92da33af01d4fb770e98c0325f41d3ebaf8986da712c82bcd4d55"
        + "4bf0b54023c29b624de9ef9c2f931efc580f9afb…]", byteString.toString());
  }

  @Test public void javaSerializationTestNonEmpty() throws Exception {
    ByteString byteString = factory.encodeUtf8(bronzeHorseman);
    assertEquivalent(byteString, TestUtil.reserialize(byteString));
  }

  @Test public void javaSerializationTestEmpty() throws Exception {
    ByteString byteString = factory.decodeHex("");
    assertEquivalent(byteString, TestUtil.reserialize(byteString));
  }

  @Test public void compareToSingleBytes() throws Exception {
    List<ByteString> originalByteStrings = Arrays.asList(
        factory.decodeHex("00"),
        factory.decodeHex("01"),
        factory.decodeHex("7e"),
        factory.decodeHex("7f"),
        factory.decodeHex("80"),
        factory.decodeHex("81"),
        factory.decodeHex("fe"),
        factory.decodeHex("ff"));

    List<ByteString> sortedByteStrings = new ArrayList<>(originalByteStrings);
    Collections.shuffle(sortedByteStrings, new Random(0));
    Collections.sort(sortedByteStrings);

    assertEquals(originalByteStrings, sortedByteStrings);
  }

  @Test public void compareToMultipleBytes() throws Exception {
    List<ByteString> originalByteStrings = Arrays.asList(
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
        factory.decodeHex("ffffff"));

    List<ByteString> sortedByteStrings = new ArrayList<>(originalByteStrings);
    Collections.shuffle(sortedByteStrings, new Random(0));
    Collections.sort(sortedByteStrings);

    assertEquals(originalByteStrings, sortedByteStrings);
  }

  @Test public void asByteBuffer() {
    assertEquals(0x42, ByteString.of((byte) 0x41, (byte) 0x42, (byte) 0x43).asByteBuffer().get(1));
  }
}
