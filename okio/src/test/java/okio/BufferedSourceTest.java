/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static okio.TestUtil.assertByteArrayEquals;
import static okio.TestUtil.assertByteArraysEquals;
import static okio.TestUtil.repeat;
import static okio.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class BufferedSourceTest {
  private static final Factory BUFFER_FACTORY = new Factory() {
    @Override public Pipe pipe() {
      Buffer buffer = new Buffer();
      Pipe result = new Pipe();
      result.sink = buffer;
      result.source = buffer;
      return result;
    }

    @Override public String toString() {
      return "Buffer";
    }
  };

  private static final Factory REAL_BUFFERED_SOURCE_FACTORY = new Factory() {
    @Override public Pipe pipe() {
      Buffer buffer = new Buffer();
      Pipe result = new Pipe();
      result.sink = buffer;
      result.source = new RealBufferedSource(buffer);
      return result;
    }

    @Override public String toString() {
      return "RealBufferedSource";
    }
  };

  private static final Factory ONE_BYTE_AT_A_TIME_FACTORY = new Factory() {
    @Override public Pipe pipe() {
      Buffer buffer = new Buffer();
      Pipe result = new Pipe();
      result.sink = buffer;
      result.source = new RealBufferedSource(new ForwardingSource(buffer) {
        @Override public long read(Buffer sink, long byteCount) throws IOException {
          return super.read(sink, Math.min(byteCount, 1L));
        }
      });
      return result;
    }

    @Override public String toString() {
      return "OneByteAtATime";
    }
  };

  private interface Factory {
    Pipe pipe();
  }

  private static class Pipe {
    BufferedSink sink;
    BufferedSource source;
  }

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return Arrays.asList(
        new Object[] { BUFFER_FACTORY },
        new Object[] { REAL_BUFFERED_SOURCE_FACTORY },
        new Object[] { ONE_BYTE_AT_A_TIME_FACTORY });
  }

  @Parameterized.Parameter
  public Factory factory;
  private BufferedSink sink;
  private BufferedSource source;

  @Before public void setUp() {
    Pipe pipe = factory.pipe();
    sink = pipe.sink;
    source = pipe.source;
  }

  @Test public void readBytes() throws Exception {
    sink.write(new byte[] { (byte) 0xab, (byte) 0xcd });
    assertEquals(0xab, source.readByte() & 0xff);
    assertEquals(0xcd, source.readByte() & 0xff);
    assertTrue(source.exhausted());
  }

  @Test public void readShort() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01
    });
    assertEquals((short) 0xabcd, source.readShort());
    assertEquals((short) 0xef01, source.readShort());
    assertTrue(source.exhausted());
  }

  @Test public void readShortLe() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10
    });
    assertEquals((short) 0xcdab, source.readShortLe());
    assertEquals((short) 0x10ef, source.readShortLe());
    assertTrue(source.exhausted());
  }

  @Test public void readShortSplitAcrossMultipleSegments() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE - 1));
    sink.write(new byte[] { (byte) 0xab, (byte) 0xcd });
    source.skip(Segment.SIZE - 1);
    assertEquals((short) 0xabcd, source.readShort());
    assertTrue(source.exhausted());
  }

  @Test public void readInt() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21
    });
    assertEquals(0xabcdef01, source.readInt());
    assertEquals(0x87654321, source.readInt());
    assertTrue(source.exhausted());
  }

  @Test public void readIntLe() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21
    });
    assertEquals(0x10efcdab, source.readIntLe());
    assertEquals(0x21436587, source.readIntLe());
    assertTrue(source.exhausted());
  }

  @Test public void readIntSplitAcrossMultipleSegments() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE - 3));
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01
    });
    source.skip(Segment.SIZE - 3);
    assertEquals(0xabcdef01, source.readInt());
    assertTrue(source.exhausted());
  }

  @Test public void readLong() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21, (byte) 0x36, (byte) 0x47, (byte) 0x58, (byte) 0x69, (byte) 0x12, (byte) 0x23,
        (byte) 0x34, (byte) 0x45
    });
    assertEquals(0xabcdef1087654321L, source.readLong());
    assertEquals(0x3647586912233445L, source.readLong());
    assertTrue(source.exhausted());
  }

  @Test public void readLongLe() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21, (byte) 0x36, (byte) 0x47, (byte) 0x58, (byte) 0x69, (byte) 0x12, (byte) 0x23,
        (byte) 0x34, (byte) 0x45
    });
    assertEquals(0x2143658710efcdabL, source.readLongLe());
    assertEquals(0x4534231269584736L, source.readLongLe());
    assertTrue(source.exhausted());
  }

  @Test public void readLongSplitAcrossMultipleSegments() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE - 7));
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21,
    });
    source.skip(Segment.SIZE - 7);
    assertEquals(0xabcdef0187654321L, source.readLong());
    assertTrue(source.exhausted());
  }

  @Test public void readAll() throws IOException {
    source.buffer().writeUtf8("abc");
    sink.writeUtf8("def");

    Buffer sink = new Buffer();
    assertEquals(6, source.readAll(sink));
    assertEquals("abcdef", sink.readUtf8());
    assertTrue(source.exhausted());
  }

  @Test public void readAllExhausted() throws IOException {
    MockSink mockSink = new MockSink();
    assertEquals(0, source.readAll(mockSink));
    assertTrue(source.exhausted());
    mockSink.assertLog();
  }

  @Test public void readExhaustedSource() throws Exception {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat('a', 10));
    assertEquals(-1, source.read(sink, 10));
    assertEquals(10, sink.size());
    assertTrue(source.exhausted());
  }

  @Test public void readZeroBytesFromSource() throws Exception {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat('a', 10));

    // Either 0 or -1 is reasonable here. For consistency with Android's
    // ByteArrayInputStream we return 0.
    assertEquals(-1, source.read(sink, 0));
    assertEquals(10, sink.size());
    assertTrue(source.exhausted());
  }

  @Test public void readFully() throws Exception {
    sink.writeUtf8(repeat('a', 10000));
    Buffer sink = new Buffer();
    source.readFully(sink, 9999);
    assertEquals(repeat('a', 9999), sink.readUtf8());
    assertEquals("a", source.readUtf8());
  }

  @Test public void readFullyTooShortThrows() throws IOException {
    sink.writeUtf8("Hi");
    Buffer sink = new Buffer();
    try {
      source.readFully(sink, 5);
      fail();
    } catch (EOFException ignored) {
    }

    // Verify we read all that we could from the source.
    assertEquals("Hi", sink.readUtf8());
  }

  @Test public void readFullyByteArray() throws IOException {
    Buffer data = new Buffer();
    data.writeUtf8("Hello").writeUtf8(repeat('e', Segment.SIZE));

    byte[] expected = data.clone().readByteArray();
    sink.write(data, data.size());

    byte[] sink = new byte[Segment.SIZE + 5];
    source.readFully(sink);
    assertByteArraysEquals(expected, sink);
  }

  @Test public void readFullyByteArrayTooShortThrows() throws IOException {
    sink.writeUtf8("Hello");

    byte[] sink = new byte[6];
    try {
      source.readFully(sink);
      fail();
    } catch (EOFException ignored) {
    }

    // Verify we read all that we could from the source.
    assertByteArraysEquals(new byte[] { 'H', 'e', 'l', 'l', 'o', 0 }, sink);
  }

  @Test public void readIntoByteArray() throws IOException {
    sink.writeUtf8("abcd");

    byte[] sink = new byte[3];
    int read = source.read(sink);
    if (factory == ONE_BYTE_AT_A_TIME_FACTORY) {
      assertEquals(1, read);
      byte[] expected = { 'a', 0, 0 };
      assertByteArraysEquals(expected, sink);
    } else {
      assertEquals(3, read);
      byte[] expected = { 'a', 'b', 'c' };
      assertByteArraysEquals(expected, sink);
    }
  }

  @Test public void readIntoByteArrayNotEnough() throws IOException {
    sink.writeUtf8("abcd");

    byte[] sink = new byte[5];
    int read = source.read(sink);
    if (factory == ONE_BYTE_AT_A_TIME_FACTORY) {
      assertEquals(1, read);
      byte[] expected = { 'a', 0, 0, 0, 0 };
      assertByteArraysEquals(expected, sink);
    } else {
      assertEquals(4, read);
      byte[] expected = { 'a', 'b', 'c', 'd', 0 };
      assertByteArraysEquals(expected, sink);
    }
  }

  @Test public void readIntoByteArrayOffsetAndCount() throws IOException {
    sink.writeUtf8("abcd");

    byte[] sink = new byte[7];
    int read = source.read(sink, 2, 3);
    if (factory == ONE_BYTE_AT_A_TIME_FACTORY) {
      assertEquals(1, read);
      byte[] expected = { 0, 0, 'a', 0, 0, 0, 0 };
      assertByteArraysEquals(expected, sink);
    } else {
      assertEquals(3, read);
      byte[] expected = { 0, 0, 'a', 'b', 'c', 0, 0 };
      assertByteArraysEquals(expected, sink);
    }
  }

  @Test public void readByteArray() throws IOException {
    String string = "abcd" + repeat('e', Segment.SIZE);
    sink.writeUtf8(string);
    assertByteArraysEquals(string.getBytes(UTF_8), source.readByteArray());
  }

  @Test public void readByteArrayPartial() throws IOException {
    sink.writeUtf8("abcd");
    assertEquals("[97, 98, 99]", Arrays.toString(source.readByteArray(3)));
    assertEquals("d", source.readUtf8(1));
  }

  @Test public void readByteString() throws IOException {
    sink.writeUtf8("abcd").writeUtf8(repeat('e', Segment.SIZE));
    assertEquals("abcd" + repeat('e', Segment.SIZE), source.readByteString().utf8());
  }

  @Test public void readByteStringPartial() throws IOException {
    sink.writeUtf8("abcd").writeUtf8(repeat('e', Segment.SIZE));
    assertEquals("abc", source.readByteString(3).utf8());
    assertEquals("d", source.readUtf8(1));
  }

  @Test public void readSpecificCharsetPartial() throws Exception {
    sink.write(
        ByteString.decodeHex("0000007600000259000002c80000006c000000e40000007300000259"
            + "000002cc000000720000006100000070000000740000025900000072"));
    assertEquals("vəˈläsə", source.readString(7 * 4, Charset.forName("utf-32")));
  }

  @Test public void readSpecificCharset() throws Exception {
    sink.write(
        ByteString.decodeHex("0000007600000259000002c80000006c000000e40000007300000259"
            + "000002cc000000720000006100000070000000740000025900000072"));
    assertEquals("vəˈläsəˌraptər", source.readString(Charset.forName("utf-32")));
  }

  @Test public void readUtf8SpansSegments() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE * 2));
    source.skip(Segment.SIZE - 1);
    assertEquals("aa", source.readUtf8(2));
  }

  @Test public void readUtf8Segment() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE));
    assertEquals(repeat('a', Segment.SIZE), source.readUtf8(Segment.SIZE));
  }

  @Test public void readUtf8PartialBuffer() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE + 20));
    assertEquals(repeat('a', Segment.SIZE + 10), source.readUtf8(Segment.SIZE + 10));
  }

  @Test public void readUtf8EntireBuffer() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE * 2));
    assertEquals(repeat('a', Segment.SIZE * 2), source.readUtf8());
  }

  @Test public void skip() throws Exception {
    sink.writeUtf8("a");
    sink.writeUtf8(repeat('b', Segment.SIZE));
    sink.writeUtf8("c");
    source.skip(1);
    assertEquals('b', source.readByte() & 0xff);
    source.skip(Segment.SIZE - 2);
    assertEquals('b', source.readByte() & 0xff);
    source.skip(1);
    assertTrue(source.exhausted());
  }

  @Test public void skipInsufficientData() throws Exception {
    sink.writeUtf8("a");

    try {
      source.skip(2);
      fail();
    } catch (EOFException ignored) {
    }
  }

  @Test public void indexOf() throws Exception {
    // The segment is empty.
    assertEquals(-1, source.indexOf((byte) 'a'));

    // The segment has one value.
    sink.writeUtf8("a"); // a
    assertEquals(0, source.indexOf((byte) 'a'));
    assertEquals(-1, source.indexOf((byte) 'b'));

    // The segment has lots of data.
    sink.writeUtf8(repeat('b', Segment.SIZE - 2)); // ab...b
    assertEquals(0, source.indexOf((byte) 'a'));
    assertEquals(1, source.indexOf((byte) 'b'));
    assertEquals(-1, source.indexOf((byte) 'c'));

    // The segment doesn't start at 0, it starts at 2.
    source.skip(2); // b...b
    assertEquals(-1, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'b'));
    assertEquals(-1, source.indexOf((byte) 'c'));

    // The segment is full.
    sink.writeUtf8("c"); // b...bc
    assertEquals(-1, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'b'));
    assertEquals(Segment.SIZE - 3, source.indexOf((byte) 'c'));

    // The segment doesn't start at 2, it starts at 4.
    source.skip(2); // b...bc
    assertEquals(-1, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'b'));
    assertEquals(Segment.SIZE - 5, source.indexOf((byte) 'c'));

    // Two segments.
    sink.writeUtf8("d"); // b...bcd, d is in the 2nd segment.
    assertEquals(Segment.SIZE - 4, source.indexOf((byte) 'd'));
    assertEquals(-1, source.indexOf((byte) 'e'));
  }

  @Test public void indexOfWithOffset() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertEquals(-1, source.indexOf((byte) 'a', 1));
    assertEquals(15, source.indexOf((byte) 'b', 15));
  }

  @Test public void indexOfByteString() throws IOException {
    assertEquals(-1, source.indexOf(ByteString.encodeUtf8("flop")));

    sink.writeUtf8("flip flop");
    assertEquals(5, source.indexOf(ByteString.encodeUtf8("flop")));
    source.readUtf8(); // Clear stream.

    // Make sure we backtrack and resume searching after partial match.
    sink.writeUtf8("hi hi hi hey");
    assertEquals(3, source.indexOf(ByteString.encodeUtf8("hi hi hey")));
  }

  @Test public void indexOfByteStringWithOffset() throws IOException {
    assertEquals(-1, source.indexOf(ByteString.encodeUtf8("flop"), 1));

    sink.writeUtf8("flop flip flop");
    assertEquals(10, source.indexOf(ByteString.encodeUtf8("flop"), 1));
    source.readUtf8(); // Clear stream

    // Make sure we backtrack and resume searching after partial match.
    sink.writeUtf8("hi hi hi hi hey");
    assertEquals(6, source.indexOf(ByteString.encodeUtf8("hi hi hey"), 1));
  }

  @Test public void indexOfByteStringInvalidArgumentsThrows() throws IOException {
    try {
      source.indexOf(ByteString.of());
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("bytes is empty", e.getMessage());
    }
    try {
      source.indexOf(ByteString.encodeUtf8("hi"), -1);
      fail();
    } catch (IllegalArgumentException e) {
      assertEquals("fromIndex < 0", e.getMessage());
    }
  }

  @Test public void indexOfElement() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertEquals(0, source.indexOfElement(ByteString.encodeUtf8("DEFGaHIJK")));
    assertEquals(1, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJKb")));
    assertEquals(Segment.SIZE + 1, source.indexOfElement(ByteString.encodeUtf8("cDEFGHIJK")));
    assertEquals(1, source.indexOfElement(ByteString.encodeUtf8("DEFbGHIc")));
    assertEquals(-1L, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJK")));
    assertEquals(-1L, source.indexOfElement(ByteString.encodeUtf8("")));
  }

  @Test public void indexOfElementWithOffset() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertEquals(-1, source.indexOfElement(ByteString.encodeUtf8("DEFGaHIJK"), 1));
    assertEquals(15, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJKb"), 15));
  }

  @Test public void request() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertTrue(source.request(Segment.SIZE + 2));
    assertFalse(source.request(Segment.SIZE + 3));
  }

  @Test public void require() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    source.require(Segment.SIZE + 2);
    try {
      source.require(Segment.SIZE + 3);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void inputStream() throws Exception {
    sink.writeUtf8("abc");
    InputStream in = source.inputStream();
    byte[] bytes = { 'z', 'z', 'z' };
    int read = in.read(bytes);
    if (factory == ONE_BYTE_AT_A_TIME_FACTORY) {
      assertEquals(1, read);
      assertByteArrayEquals("azz", bytes);

      read = in.read(bytes);
      assertEquals(1, read);
      assertByteArrayEquals("bzz", bytes);

      read = in.read(bytes);
      assertEquals(1, read);
      assertByteArrayEquals("czz", bytes);
    } else {
      assertEquals(3, read);
      assertByteArrayEquals("abc", bytes);
    }

    assertEquals(-1, in.read());
  }

  @Test public void inputStreamOffsetCount() throws Exception {
    sink.writeUtf8("abcde");
    InputStream in = source.inputStream();
    byte[] bytes = { 'z', 'z', 'z', 'z', 'z' };
    int read = in.read(bytes, 1, 3);
    if (factory == ONE_BYTE_AT_A_TIME_FACTORY) {
      assertEquals(1, read);
      assertByteArrayEquals("zazzz", bytes);
    } else {
      assertEquals(3, read);
      assertByteArrayEquals("zabcz", bytes);
    }
  }

  @Test public void inputStreamSkip() throws Exception {
    sink.writeUtf8("abcde");
    InputStream in = source.inputStream();
    assertEquals(4, in.skip(4));
    assertEquals('e', in.read());
  }

  @Test public void inputStreamCharByChar() throws Exception {
    sink.writeUtf8("abc");
    InputStream in = source.inputStream();
    assertEquals('a', in.read());
    assertEquals('b', in.read());
    assertEquals('c', in.read());
    assertEquals(-1, in.read());
  }

  @Test public void inputStreamBounds() throws IOException {
    sink.writeUtf8(repeat('a', 100));
    InputStream in = source.inputStream();
    try {
      in.read(new byte[100], 50, 51);
      fail();
    } catch (ArrayIndexOutOfBoundsException expected) {
    }
  }

  @Test public void longHexString() throws IOException {
    assertLongHexString("8000000000000000", 0x8000000000000000L);
    assertLongHexString("fffffffffffffffe", 0xFFFFFFFFFFFFFFFEL);
    assertLongHexString("FFFFFFFFFFFFFFFe", 0xFFFFFFFFFFFFFFFEL);
    assertLongHexString("ffffffffffffffff", 0xffffffffffffffffL);
    assertLongHexString("FFFFFFFFFFFFFFFF", 0xFFFFFFFFFFFFFFFFL);
    assertLongHexString("0000000000000000", 0x0);
    assertLongHexString("0000000000000001", 0x1);
    assertLongHexString("7999999999999999", 0x7999999999999999L);

    assertLongHexString("FF", 0xFF);
    assertLongHexString("0000000000000001", 0x1);
  }

  @Test public void hexStringWithManyLeadingZeros() throws IOException {
    assertLongHexString("00000000000000001", 0x1);
    assertLongHexString("0000000000000000ffffffffffffffff", 0xffffffffffffffffL);
    assertLongHexString("00000000000000007fffffffffffffff", 0x7fffffffffffffffL);
    assertLongHexString(TestUtil.repeat('0', Segment.SIZE + 1) + "1", 0x1);
  }

  private void assertLongHexString(String s, long expected) throws IOException {
    sink.writeUtf8(s);
    long actual = source.readHexadecimalUnsignedLong();
    assertEquals(s + " --> " + expected, expected, actual);
  }

  @Test public void longHexStringAcrossSegment() throws IOException {
    sink.writeUtf8(repeat('a', Segment.SIZE - 8)).writeUtf8("FFFFFFFFFFFFFFFF");
    source.skip(Segment.SIZE - 8);
    assertEquals(-1, source.readHexadecimalUnsignedLong());
  }

  @Test public void longHexStringTooLongThrows() throws IOException {
    try {
      sink.writeUtf8("fffffffffffffffff");
      source.readHexadecimalUnsignedLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Number too large: fffffffffffffffff", e.getMessage());
    }
  }

  @Test public void longHexStringTooShortThrows() throws IOException {
    try {
      sink.writeUtf8(" ");
      source.readHexadecimalUnsignedLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Expected leading [0-9a-fA-F] character but was 0x20", e.getMessage());
    }
  }

  @Test public void longHexEmptySourceThrows() throws IOException {
    try {
      sink.writeUtf8("");
      source.readHexadecimalUnsignedLong();
      fail();
    } catch (IllegalStateException | EOFException expected) {
    }
  }

  @Test public void longDecimalString() throws IOException {
    assertLongDecimalString("-9223372036854775808", -9223372036854775808L);
    assertLongDecimalString("-1", -1L);
    assertLongDecimalString("0", 0L);
    assertLongDecimalString("1", 1L);
    assertLongDecimalString("9223372036854775807", 9223372036854775807L);

    assertLongDecimalString("00000001", 1L);
    assertLongDecimalString("-000001", -1L);
  }

  private void assertLongDecimalString(String s, long expected) throws IOException {
    sink.writeUtf8(s);
    sink.writeUtf8("zzz");
    long actual = source.readDecimalLong();
    assertEquals(s + " --> " + expected, expected, actual);
    assertEquals("zzz", source.readUtf8());
  }

  @Test public void longDecimalStringAcrossSegment() throws IOException {
    sink.writeUtf8(repeat('a', Segment.SIZE - 8)).writeUtf8("1234567890123456");
    sink.writeUtf8("zzz");
    source.skip(Segment.SIZE - 8);
    assertEquals(1234567890123456L, source.readDecimalLong());
    assertEquals("zzz", source.readUtf8());
  }

  @Test public void longDecimalStringTooLongThrows() throws IOException {
    try {
      sink.writeUtf8("12345678901234567890"); // Too many digits.
      source.readDecimalLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Number too large: 12345678901234567890", e.getMessage());
    }
  }

  @Test public void longDecimalStringTooHighThrows() throws IOException {
    try {
      sink.writeUtf8("9223372036854775808"); // Right size but cannot fit.
      source.readDecimalLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Number too large: 9223372036854775808", e.getMessage());
    }
  }

  @Test public void longDecimalStringTooLowThrows() throws IOException {
    try {
      sink.writeUtf8("-9223372036854775809"); // Right size but cannot fit.
      source.readDecimalLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Number too large: -9223372036854775809", e.getMessage());
    }
  }

  @Test public void longDecimalStringTooShortThrows() throws IOException {
    try {
      sink.writeUtf8(" ");
      source.readDecimalLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Expected leading [0-9] or '-' character but was 0x20", e.getMessage());
    }
  }

  @Test public void longDecimalEmptyThrows() throws IOException {
    try {
      sink.writeUtf8("");
      source.readDecimalLong();
      fail();
    } catch (IllegalStateException | EOFException expected) {
    }
  }

  @Test public void codePoints() throws IOException {
    sink.write(ByteString.decodeHex("7f"));
    assertEquals(0x7f, source.readUtf8CodePoint());

    sink.write(ByteString.decodeHex("dfbf"));
    assertEquals(0x07ff, source.readUtf8CodePoint());

    sink.write(ByteString.decodeHex("efbfbf"));
    assertEquals(0xffff, source.readUtf8CodePoint());

    sink.write(ByteString.decodeHex("f48fbfbf"));
    assertEquals(0x10ffff, source.readUtf8CodePoint());
  }

  @Test public void decimalStringWithManyLeadingZeros() throws IOException {
    assertLongDecimalString("00000000000000001", 1);
    assertLongDecimalString("00000000000000009223372036854775807", 9223372036854775807L);
    assertLongDecimalString("-00000000000000009223372036854775808", -9223372036854775808L);
    assertLongDecimalString(TestUtil.repeat('0', Segment.SIZE + 1) + "1", 1);
  }
}
