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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static okio.TestUtil.assertByteArrayEquals;
import static okio.TestUtil.assertByteArraysEquals;
import static okio.TestUtil.repeat;
import static okio.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public final class BufferedSourceTest {
  interface Factory {
    Factory BUFFER = new Factory() {
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

    Factory REAL_BUFFERED_SOURCE = new Factory() {
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

    /**
     * A factory deliberately written to create buffers whose internal segments are always 1 byte
     * long. We like testing with these segments because are likely to trigger bugs!
     */
    Factory ONE_BYTE_AT_A_TIME = new Factory() {
      @Override public Pipe pipe() {
        Buffer buffer = new Buffer();
        Pipe result = new Pipe();
        result.sink = buffer;
        result.source = new RealBufferedSource(new ForwardingSource(buffer) {
          @Override public long read(Buffer sink, long byteCount) throws IOException {
            // This reads a byte into a new buffer, then clones it so that the segments are shared.
            // Shared segments cannot be compacted so we'll get a long chain of short segments.
            Buffer box = new Buffer();
            long result = super.read(box, Math.min(byteCount, 1L));
            if (result > 0L) sink.write(box.clone(), result);
            return result;
          }
        });
        return result;
      }

      @Override public String toString() {
        return "OneByteAtATime";
      }
    };

    Pipe pipe();
  }

  private static class Pipe {
    BufferedSink sink;
    BufferedSource source;
  }

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return Arrays.asList(
        new Object[] { Factory.BUFFER},
        new Object[] { Factory.REAL_BUFFERED_SOURCE},
        new Object[] { Factory.ONE_BYTE_AT_A_TIME});
  }

  @Parameter public Factory factory;
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
    if (factory == Factory.ONE_BYTE_AT_A_TIME) {
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
    if (factory == Factory.ONE_BYTE_AT_A_TIME) {
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
    if (factory == Factory.ONE_BYTE_AT_A_TIME) {
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

  @Test public void indexOfByteWithStartOffset() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat('b', Segment.SIZE)).writeUtf8("c");
    assertEquals(-1, source.indexOf((byte) 'a', 1));
    assertEquals(15, source.indexOf((byte) 'b', 15));
  }

  @Test public void indexOfByteWithBothOffsets() throws IOException {
    if (factory == Factory.ONE_BYTE_AT_A_TIME) {
      // When run on Travis, ONE_BYTE_AT_A_TIME
      // causes out-of-memory errors.
      return;
    }
    byte a = (byte) 'a';
    byte c = (byte) 'c';

    int size = Segment.SIZE * 5;
    byte[] bytes = new byte[size];
    Arrays.fill(bytes, a);

    // These are tricky places where the buffer
    // starts, ends, or segments come together.
    int[] points = {
        0,                       1,                   2,
        Segment.SIZE - 1,        Segment.SIZE,        Segment.SIZE + 1,
        size / 2 - 1,            size / 2,            size / 2 + 1,
        size - Segment.SIZE - 1, size - Segment.SIZE, size - Segment.SIZE + 1,
        size - 3,                size - 2,            size - 1
    };

    // In each iteration, we write c to the known point and then search for it using different
    // windows. Some of the windows don't overlap with c's position, and therefore a match shouldn't
    // be found.
    for (int p : points) {
      bytes[p] = c;
      sink.write(bytes);

      assertEquals( p, source.indexOf(c, 0,      size     ));
      assertEquals( p, source.indexOf(c, 0,      p + 1    ));
      assertEquals( p, source.indexOf(c, p,      size     ));
      assertEquals( p, source.indexOf(c, p,      p + 1    ));
      assertEquals( p, source.indexOf(c, p / 2,  p * 2 + 1));
      assertEquals(-1, source.indexOf(c, 0,      p / 2    ));
      assertEquals(-1, source.indexOf(c, 0,      p        ));
      assertEquals(-1, source.indexOf(c, 0,      0        ));
      assertEquals(-1, source.indexOf(c, p,      p        ));

      // Reset.
      source.readUtf8();
      bytes[p] = a;
    }
  }

  @Test public void indexOfByteInvalidBoundsThrows() throws IOException {
    sink.writeUtf8("abc");

    try {
      source.indexOf((byte) 'a', -1);
      fail("Expected failure: fromIndex < 0");
    } catch (IllegalArgumentException expected) {
    }

    try {
      source.indexOf((byte) 'a', 10, 0);
      fail("Expected failure: fromIndex > toIndex");
    } catch (IllegalArgumentException expected) {
    }
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

  @Test public void indexOfByteStringAtSegmentBoundary() throws IOException {
    sink.writeUtf8(repeat('a', Segment.SIZE - 1));
    sink.writeUtf8("bcd");
    assertEquals(Segment.SIZE - 3, source.indexOf(ByteString.encodeUtf8("aabc"), Segment.SIZE - 4));
    assertEquals(Segment.SIZE - 3, source.indexOf(ByteString.encodeUtf8("aabc"), Segment.SIZE - 3));
    assertEquals(Segment.SIZE - 2, source.indexOf(ByteString.encodeUtf8("abcd"), Segment.SIZE - 2));
    assertEquals(Segment.SIZE - 2, source.indexOf(ByteString.encodeUtf8("abc"),  Segment.SIZE - 2));
    assertEquals(Segment.SIZE - 2, source.indexOf(ByteString.encodeUtf8("abc"),  Segment.SIZE - 2));
    assertEquals(Segment.SIZE - 2, source.indexOf(ByteString.encodeUtf8("ab"),   Segment.SIZE - 2));
    assertEquals(Segment.SIZE - 2, source.indexOf(ByteString.encodeUtf8("a"),    Segment.SIZE - 2));
    assertEquals(Segment.SIZE - 1, source.indexOf(ByteString.encodeUtf8("bc"),   Segment.SIZE - 2));
    assertEquals(Segment.SIZE - 1, source.indexOf(ByteString.encodeUtf8("b"),    Segment.SIZE - 2));
    assertEquals(Segment.SIZE,     source.indexOf(ByteString.encodeUtf8("c"),    Segment.SIZE - 2));
    assertEquals(Segment.SIZE,     source.indexOf(ByteString.encodeUtf8("c"),    Segment.SIZE    ));
    assertEquals(Segment.SIZE + 1, source.indexOf(ByteString.encodeUtf8("d"),    Segment.SIZE - 2));
    assertEquals(Segment.SIZE + 1, source.indexOf(ByteString.encodeUtf8("d"),    Segment.SIZE + 1));
  }

  @Test public void indexOfDoesNotWrapAround() throws IOException {
    sink.writeUtf8(repeat('a', Segment.SIZE - 1));
    sink.writeUtf8("bcd");
    assertEquals(-1, source.indexOf(ByteString.encodeUtf8("abcda"), Segment.SIZE - 3));
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

  /**
   * With {@link Factory#ONE_BYTE_AT_A_TIME}, this code was extremely slow.
   * https://github.com/square/okio/issues/171
   */
  @Test public void indexOfByteStringAcrossSegmentBoundaries() throws IOException {
    sink.writeUtf8(repeat('a', Segment.SIZE * 2 - 3));
    sink.writeUtf8("bcdefg");
    assertEquals(Segment.SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("ab")));
    assertEquals(Segment.SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abc")));
    assertEquals(Segment.SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abcd")));
    assertEquals(Segment.SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abcde")));
    assertEquals(Segment.SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abcdef")));
    assertEquals(Segment.SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abcdefg")));
    assertEquals(Segment.SIZE * 2 - 3, source.indexOf(ByteString.encodeUtf8("bcdefg")));
    assertEquals(Segment.SIZE * 2 - 2, source.indexOf(ByteString.encodeUtf8("cdefg")));
    assertEquals(Segment.SIZE * 2 - 1, source.indexOf(ByteString.encodeUtf8("defg")));
    assertEquals(Segment.SIZE * 2,     source.indexOf(ByteString.encodeUtf8("efg")));
    assertEquals(Segment.SIZE * 2 + 1, source.indexOf(ByteString.encodeUtf8("fg")));
    assertEquals(Segment.SIZE * 2 + 2, source.indexOf(ByteString.encodeUtf8("g")));
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
    if (factory == Factory.ONE_BYTE_AT_A_TIME) {
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
    if (factory == Factory.ONE_BYTE_AT_A_TIME) {
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

    sink.writeUtf8("abcde");
    assertEquals(5, in.skip(10)); // Try to skip too much.
    assertEquals(0, in.skip(1)); // Try to skip when exhausted.
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

  @Test public void select() throws IOException {
    Options options = Options.of(
        ByteString.encodeUtf8("ROCK"),
        ByteString.encodeUtf8("SCISSORS"),
        ByteString.encodeUtf8("PAPER"));

    sink.writeUtf8("PAPER,SCISSORS,ROCK");
    assertEquals(2, source.select(options));
    assertEquals(',', source.readByte());
    assertEquals(1, source.select(options));
    assertEquals(',', source.readByte());
    assertEquals(0, source.select(options));
    assertTrue(source.exhausted());
  }

  @Test public void selectSpanningMultipleSegments() throws IOException {
    ByteString commonPrefix = TestUtil.randomBytes(Segment.SIZE + 10);
    ByteString a = new Buffer().write(commonPrefix).writeUtf8("a").readByteString();
    ByteString bc = new Buffer().write(commonPrefix).writeUtf8("bc").readByteString();
    ByteString bd = new Buffer().write(commonPrefix).writeUtf8("bd").readByteString();
    Options options = Options.of(a, bc, bd);

    sink.write(bd);
    sink.write(a);
    sink.write(bc);

    assertEquals(2, source.select(options));
    assertEquals(0, source.select(options));
    assertEquals(1, source.select(options));
    assertTrue(source.exhausted());
  }

  @Test public void selectNotFound() throws IOException {
    Options options = Options.of(
        ByteString.encodeUtf8("ROCK"),
        ByteString.encodeUtf8("SCISSORS"),
        ByteString.encodeUtf8("PAPER"));

    sink.writeUtf8("SPOCK");
    assertEquals(-1, source.select(options));
    assertEquals("SPOCK", source.readUtf8());
  }

  @Test public void selectValuesHaveCommonPrefix() throws IOException {
    Options options = Options.of(
        ByteString.encodeUtf8("abcd"),
        ByteString.encodeUtf8("abce"),
        ByteString.encodeUtf8("abcc"));

    sink.writeUtf8("abcc").writeUtf8("abcd").writeUtf8("abce");
    assertEquals(2, source.select(options));
    assertEquals(0, source.select(options));
    assertEquals(1, source.select(options));
  }

  @Test public void selectLongerThanSource() throws IOException {
    Options options = Options.of(
        ByteString.encodeUtf8("abcd"),
        ByteString.encodeUtf8("abce"),
        ByteString.encodeUtf8("abcc"));
    sink.writeUtf8("abc");
    assertEquals(-1, source.select(options));
    assertEquals("abc", source.readUtf8());
  }

  @Test public void selectReturnsFirstByteStringThatMatches() throws IOException {
    Options options = Options.of(
        ByteString.encodeUtf8("abcd"),
        ByteString.encodeUtf8("abc"),
        ByteString.encodeUtf8("abcde"));
    sink.writeUtf8("abcdef");
    assertEquals(0, source.select(options));
    assertEquals("ef", source.readUtf8());
  }

  @Test public void selectNoByteStrings() throws IOException {
    Options options = Options.of();
    sink.writeUtf8("abc");
    assertEquals(-1, source.select(options));
  }

  @Test public void selectFromEmptySource() throws IOException {
    Options options = Options.of(
        ByteString.encodeUtf8("abc"),
        ByteString.encodeUtf8("def"));
    assertEquals(-1, source.select(options));
  }

  @Test public void selectNoByteStringsFromEmptySource() throws IOException {
    Options options = Options.of();
    assertEquals(-1, source.select(options));
  }

  @Test public void selectEmptyByteString() throws IOException {
    Options options = Options.of(ByteString.of());
    sink.writeUtf8("abc");
    assertEquals(0, source.select(options));
    assertEquals("abc", source.readUtf8());
  }

  @Test public void selectEmptyByteStringFromEmptySource() throws IOException {
    Options options = Options.of(ByteString.of());
    assertEquals(0, source.select(options));
  }

  @Test public void rangeEquals() throws IOException {
    sink.writeUtf8("A man, a plan, a canal. Panama.");
    assertTrue(source.rangeEquals(7 , ByteString.encodeUtf8("a plan")));
    assertTrue(source.rangeEquals(0 , ByteString.encodeUtf8("A man")));
    assertTrue(source.rangeEquals(24, ByteString.encodeUtf8("Panama")));
    assertFalse(source.rangeEquals(24, ByteString.encodeUtf8("Panama. Panama. Panama.")));
  }

  @Test public void rangeEqualsWithOffsetAndCount() throws IOException {
    sink.writeUtf8("A man, a plan, a canal. Panama.");
    assertTrue(source.rangeEquals(7 , ByteString.encodeUtf8("aaa plannn"), 2, 6));
    assertTrue(source.rangeEquals(0 , ByteString.encodeUtf8("AAA mannn"), 2, 5));
    assertTrue(source.rangeEquals(24, ByteString.encodeUtf8("PPPanamaaa"), 2, 6));
  }

  @Test public void rangeEqualsOnlyReadsUntilMismatch() throws IOException {
    assumeTrue(factory == Factory.ONE_BYTE_AT_A_TIME); // Other sources read in chunks anyway.

    sink.writeUtf8("A man, a plan, a canal. Panama.");
    assertFalse(source.rangeEquals(0, ByteString.encodeUtf8("A man.")));
    assertEquals("A man,", source.buffer().readUtf8());
  }

  @Test public void rangeEqualsArgumentValidation() throws IOException {
    // Negative source offset.
    assertFalse(source.rangeEquals(-1, ByteString.encodeUtf8("A")));
    // Negative bytes offset.
    assertFalse(source.rangeEquals(0, ByteString.encodeUtf8("A"), -1, 1));
    // Bytes offset longer than bytes length.
    assertFalse(source.rangeEquals(0, ByteString.encodeUtf8("A"), 2, 1));
    // Negative byte count.
    assertFalse(source.rangeEquals(0, ByteString.encodeUtf8("A"), 0, -1));
    // Byte count longer than bytes length.
    assertFalse(source.rangeEquals(0, ByteString.encodeUtf8("A"), 0, 2));
    // Bytes offset plus byte count longer than bytes length.
    assertFalse(source.rangeEquals(0, ByteString.encodeUtf8("A"), 1, 1));
  }
}
