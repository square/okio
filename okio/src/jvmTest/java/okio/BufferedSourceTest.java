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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static kotlin.text.Charsets.US_ASCII;
import static kotlin.text.Charsets.UTF_8;
import static kotlin.text.StringsKt.repeat;
import static okio.TestUtil.SEGMENT_SIZE;
import static okio.TestUtil.assertByteArrayEquals;
import static okio.TestUtil.assertByteArraysEquals;
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

      @Override public boolean isOneByteAtATime() {
        return false;
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
        result.source = Okio.buffer((Source) buffer);
        return result;
      }

      @Override public boolean isOneByteAtATime() {
        return false;
      }

      @Override public String toString() {
        return "RealBufferedSource";
      }
    };

    /**
     * A factory deliberately written to create buffers whose internal segments are always 1 byte
     * long. We like testing with these segments because are likely to trigger bugs!
     */
    Factory ONE_BYTE_AT_A_TIME_BUFFERED_SOURCE = new Factory() {
      @Override public Pipe pipe() {
        Buffer buffer = new Buffer();
        Pipe result = new Pipe();
        result.sink = buffer;
        result.source = Okio.buffer(new ForwardingSource(buffer) {
          @Override public long read(Buffer sink, long byteCount) throws IOException {
            // Read one byte into a new buffer, then clone it so that the segment is shared.
            // Shared segments cannot be compacted so we'll get a long chain of short segments.
            Buffer box = new Buffer();
            long result = super.read(box, Math.min(byteCount, 1L));
            if (result > 0L) sink.write(box.clone(), result);
            return result;
          }
        });
        return result;
      }

      @Override public boolean isOneByteAtATime() {
        return true;
      }

      @Override public String toString() {
        return "OneByteAtATimeBufferedSource";
      }
    };

    Factory ONE_BYTE_AT_A_TIME_BUFFER = new Factory() {
      @Override public Pipe pipe() {
        Buffer buffer = new Buffer();
        Pipe result = new Pipe();
        result.source = buffer;
        result.sink = Okio.buffer(new ForwardingSink(buffer) {
          @Override public void write(Buffer source, long byteCount) throws IOException {
            // Write each byte into a new buffer, then clone it so that the segments are shared.
            // Shared segments cannot be compacted so we'll get a long chain of short segments.
            for (int i = 0; i < byteCount; i++) {
              Buffer box = new Buffer();
              box.write(source, 1);
              super.write(box.clone(), 1);
            }
          }
        });
        return result;
      }

      @Override public boolean isOneByteAtATime() {
        return true;
      }

      @Override public String toString() {
        return "OneByteAtATimeBuffer";
      }
    };

    Factory PEEK_BUFFER = new Factory() {
      @Override public Pipe pipe() {
        Buffer buffer = new Buffer();
        Pipe result = new Pipe();
        result.sink = buffer;
        result.source = buffer.peek();
        return result;
      }

      @Override public boolean isOneByteAtATime() {
        return false;
      }

      @Override public String toString() {
        return "PeekBuffer";
      }
    };

    Factory PEEK_BUFFERED_SOURCE = new Factory() {
      @Override public Pipe pipe() {
        Buffer buffer = new Buffer();
        Pipe result = new Pipe();
        result.sink = buffer;
        result.source = Okio.buffer((Source) buffer).peek();
        return result;
      }

      @Override public boolean isOneByteAtATime() {
        return false;
      }

      @Override public String toString() {
        return "PeekBufferedSource";
      }
    };

    Pipe pipe();

    boolean isOneByteAtATime();
  }

  private static class Pipe {
    BufferedSink sink;
    BufferedSource source;
  }

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return Arrays.asList(
        new Object[] { Factory.BUFFER },
        new Object[] { Factory.REAL_BUFFERED_SOURCE },
        new Object[] { Factory.ONE_BYTE_AT_A_TIME_BUFFERED_SOURCE },
        new Object[] { Factory.ONE_BYTE_AT_A_TIME_BUFFER },
        new Object[] { Factory.PEEK_BUFFER },
        new Object[] { Factory.PEEK_BUFFERED_SOURCE });
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
    sink.emit();
    assertEquals(0xab, source.readByte() & 0xff);
    assertEquals(0xcd, source.readByte() & 0xff);
    assertTrue(source.exhausted());
  }

  @Test public void readByteTooShortThrows() throws IOException {
    try {
      source.readByte();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void readShort() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01
    });
    sink.emit();
    assertEquals((short) 0xabcd, source.readShort());
    assertEquals((short) 0xef01, source.readShort());
    assertTrue(source.exhausted());
  }

  @Test public void readShortLe() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10
    });
    sink.emit();
    assertEquals((short) 0xcdab, source.readShortLe());
    assertEquals((short) 0x10ef, source.readShortLe());
    assertTrue(source.exhausted());
  }

  @Test public void readShortSplitAcrossMultipleSegments() throws Exception {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE - 1));
    sink.write(new byte[] { (byte) 0xab, (byte) 0xcd });
    sink.emit();
    source.skip(SEGMENT_SIZE - 1);
    assertEquals((short) 0xabcd, source.readShort());
    assertTrue(source.exhausted());
  }

  @Test public void readShortTooShortThrows() throws IOException {
    sink.writeShort(Short.MAX_VALUE);
    sink.emit();
    source.readByte();
    try {
      source.readShort();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void readShortLeTooShortThrows() throws IOException {
    sink.writeShortLe(Short.MAX_VALUE);
    sink.emit();
    source.readByte();
    try {
      source.readShortLe();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void readInt() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21
    });
    sink.emit();
    assertEquals(0xabcdef01, source.readInt());
    assertEquals(0x87654321, source.readInt());
    assertTrue(source.exhausted());
  }

  @Test public void readIntLe() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21
    });
    sink.emit();
    assertEquals(0x10efcdab, source.readIntLe());
    assertEquals(0x21436587, source.readIntLe());
    assertTrue(source.exhausted());
  }

  @Test public void readIntSplitAcrossMultipleSegments() throws Exception {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE - 3));
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01
    });
    sink.emit();
    source.skip(SEGMENT_SIZE - 3);
    assertEquals(0xabcdef01, source.readInt());
    assertTrue(source.exhausted());
  }

  @Test public void readIntTooShortThrows() throws IOException {
    sink.writeInt(Integer.MAX_VALUE);
    sink.emit();
    source.readByte();
    try {
      source.readInt();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void readIntLeTooShortThrows() throws IOException {
    sink.writeIntLe(Integer.MAX_VALUE);
    sink.emit();
    source.readByte();
    try {
      source.readIntLe();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void readLong() throws Exception {
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x10, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21, (byte) 0x36, (byte) 0x47, (byte) 0x58, (byte) 0x69, (byte) 0x12, (byte) 0x23,
        (byte) 0x34, (byte) 0x45
    });
    sink.emit();
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
    sink.emit();
    assertEquals(0x2143658710efcdabL, source.readLongLe());
    assertEquals(0x4534231269584736L, source.readLongLe());
    assertTrue(source.exhausted());
  }

  @Test public void readLongSplitAcrossMultipleSegments() throws Exception {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE - 7));
    sink.write(new byte[] {
        (byte) 0xab, (byte) 0xcd, (byte) 0xef, (byte) 0x01, (byte) 0x87, (byte) 0x65, (byte) 0x43,
        (byte) 0x21,
    });
    sink.emit();
    source.skip(SEGMENT_SIZE - 7);
    assertEquals(0xabcdef0187654321L, source.readLong());
    assertTrue(source.exhausted());
  }

  @Test public void readLongTooShortThrows() throws IOException {
    sink.writeLong(Long.MAX_VALUE);
    sink.emit();
    source.readByte();
    try {
      source.readLong();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void readLongLeTooShortThrows() throws IOException {
    sink.writeLongLe(Long.MAX_VALUE);
    sink.emit();
    source.readByte();
    try {
      source.readLongLe();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void readAll() throws IOException {
    source.getBuffer().writeUtf8("abc");
    sink.writeUtf8("def");
    sink.emit();

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
    sink.writeUtf8(repeat("a", 10));
    assertEquals(-1, source.read(sink, 10));
    assertEquals(10, sink.size());
    assertTrue(source.exhausted());
  }

  @Test public void readZeroBytesFromSource() throws Exception {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat("a", 10));

    // Either 0 or -1 is reasonable here. For consistency with Android's
    // ByteArrayInputStream we return 0.
    assertEquals(-1, source.read(sink, 0));
    assertEquals(10, sink.size());
    assertTrue(source.exhausted());
  }

  @Test public void readFully() throws Exception {
    sink.writeUtf8(repeat("a", 10000));
    sink.emit();
    Buffer sink = new Buffer();
    source.readFully(sink, 9999);
    assertEquals(repeat("a", 9999), sink.readUtf8());
    assertEquals("a", source.readUtf8());
  }

  @Test public void readFullyTooShortThrows() throws IOException {
    sink.writeUtf8("Hi");
    sink.emit();
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
    data.writeUtf8("Hello").writeUtf8(repeat("e", SEGMENT_SIZE));

    byte[] expected = data.clone().readByteArray();
    sink.write(data, data.size());
    sink.emit();

    byte[] sink = new byte[SEGMENT_SIZE + 5];
    source.readFully(sink);
    assertByteArraysEquals(expected, sink);
  }

  @Test public void readFullyByteArrayTooShortThrows() throws IOException {
    sink.writeUtf8("Hello");
    sink.emit();

    byte[] array = new byte[6];
    try {
      source.readFully(array);
      fail();
    } catch (EOFException ignored) {
    }

    // Verify we read all that we could from the source.
    assertByteArraysEquals(new byte[] { 'H', 'e', 'l', 'l', 'o', 0 }, array);
  }

  @Test public void readIntoByteArray() throws IOException {
    sink.writeUtf8("abcd");
    sink.emit();

    byte[] sink = new byte[3];
    int read = source.read(sink);
    if (factory.isOneByteAtATime()) {
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
    sink.emit();

    byte[] sink = new byte[5];
    int read = source.read(sink);
    if (factory.isOneByteAtATime()) {
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
    sink.emit();

    byte[] sink = new byte[7];
    int read = source.read(sink, 2, 3);
    if (factory.isOneByteAtATime()) {
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
    String string = "abcd" + repeat("e", SEGMENT_SIZE);
    sink.writeUtf8(string);
    sink.emit();
    assertByteArraysEquals(string.getBytes(UTF_8), source.readByteArray());
  }

  @Test public void readByteArrayPartial() throws IOException {
    sink.writeUtf8("abcd");
    sink.emit();
    assertEquals("[97, 98, 99]", Arrays.toString(source.readByteArray(3)));
    assertEquals("d", source.readUtf8(1));
  }

  @Test public void readByteArrayTooShortThrows() throws IOException {
    sink.writeUtf8("abc");
    sink.emit();
    try {
      source.readByteArray(4);
      fail();
    } catch (EOFException expected) {
    }
    assertEquals("abc", source.readUtf8()); // The read shouldn't consume any data.
  }

  @Test public void readByteString() throws IOException {
    sink.writeUtf8("abcd").writeUtf8(repeat("e", SEGMENT_SIZE));
    sink.emit();
    assertEquals("abcd" + repeat("e", SEGMENT_SIZE), source.readByteString().utf8());
  }

  @Test public void readByteStringPartial() throws IOException {
    sink.writeUtf8("abcd").writeUtf8(repeat("e", SEGMENT_SIZE));
    sink.emit();
    assertEquals("abc", source.readByteString(3).utf8());
    assertEquals("d", source.readUtf8(1));
  }

  @Test public void readByteStringTooShortThrows() throws IOException {
    sink.writeUtf8("abc");
    sink.emit();
    try {
      source.readByteString(4);
      fail();
    } catch (EOFException expected) {
    }
    assertEquals("abc", source.readUtf8()); // The read shouldn't consume any data.
  }

  @Test public void readSpecificCharsetPartial() throws Exception {
    sink.write(ByteString.decodeHex("0000007600000259000002c80000006c000000e40000007300000259"
        + "000002cc000000720000006100000070000000740000025900000072"));
    sink.emit();
    assertEquals("vəˈläsə", source.readString(7 * 4, Charset.forName("utf-32")));
  }

  @Test public void readSpecificCharset() throws Exception {
    sink.write(ByteString.decodeHex("0000007600000259000002c80000006c000000e40000007300000259"
        + "000002cc000000720000006100000070000000740000025900000072"));
    sink.emit();
    assertEquals("vəˈläsəˌraptər", source.readString(Charset.forName("utf-32")));
  }

  @Test public void readStringTooShortThrows() throws IOException {
    sink.writeString("abc", US_ASCII);
    sink.emit();
    try {
      source.readString(4, US_ASCII);
      fail();
    } catch (EOFException expected) {
    }
    assertEquals("abc", source.readUtf8()); // The read shouldn't consume any data.
  }

  @Test public void readUtf8SpansSegments() throws Exception {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE * 2));
    sink.emit();
    source.skip(SEGMENT_SIZE - 1);
    assertEquals("aa", source.readUtf8(2));
  }

  @Test public void readUtf8Segment() throws Exception {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE));
    sink.emit();
    assertEquals(repeat("a", SEGMENT_SIZE), source.readUtf8(SEGMENT_SIZE));
  }

  @Test public void readUtf8PartialBuffer() throws Exception {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE + 20));
    sink.emit();
    assertEquals(repeat("a", SEGMENT_SIZE + 10), source.readUtf8(SEGMENT_SIZE + 10));
  }

  @Test public void readUtf8EntireBuffer() throws Exception {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE * 2));
    sink.emit();
    assertEquals(repeat("a", SEGMENT_SIZE * 2), source.readUtf8());
  }

  @Test public void readUtf8TooShortThrows() throws IOException {
    sink.writeUtf8("abc");
    sink.emit();
    try {
      source.readUtf8(4L);
      fail();
    } catch (EOFException expected) {
    }
    assertEquals("abc", source.readUtf8()); // The read shouldn't consume any data.
  }

  @Test public void skip() throws Exception {
    sink.writeUtf8("a");
    sink.writeUtf8(repeat("b", SEGMENT_SIZE));
    sink.writeUtf8("c");
    sink.emit();
    source.skip(1);
    assertEquals('b', source.readByte() & 0xff);
    source.skip(SEGMENT_SIZE - 2);
    assertEquals('b', source.readByte() & 0xff);
    source.skip(1);
    assertTrue(source.exhausted());
  }

  @Test public void skipInsufficientData() throws Exception {
    sink.writeUtf8("a");
    sink.emit();

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
    sink.emit();
    assertEquals(0, source.indexOf((byte) 'a'));
    assertEquals(-1, source.indexOf((byte) 'b'));

    // The segment has lots of data.
    sink.writeUtf8(repeat("b", SEGMENT_SIZE - 2)); // ab...b
    sink.emit();
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
    sink.emit();
    assertEquals(-1, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'b'));
    assertEquals(SEGMENT_SIZE - 3, source.indexOf((byte) 'c'));

    // The segment doesn't start at 2, it starts at 4.
    source.skip(2); // b...bc
    assertEquals(-1, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'b'));
    assertEquals(SEGMENT_SIZE - 5, source.indexOf((byte) 'c'));

    // Two segments.
    sink.writeUtf8("d"); // b...bcd, d is in the 2nd segment.
    sink.emit();
    assertEquals(SEGMENT_SIZE - 4, source.indexOf((byte) 'd'));
    assertEquals(-1, source.indexOf((byte) 'e'));
  }

  @Test public void indexOfByteWithStartOffset() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat("b", SEGMENT_SIZE)).writeUtf8("c");
    sink.emit();
    assertEquals(-1, source.indexOf((byte) 'a', 1));
    assertEquals(15, source.indexOf((byte) 'b', 15));
  }

  @Test public void indexOfByteWithBothOffsets() throws IOException {
    if (factory.isOneByteAtATime()) {
      // When run on Travis this causes out-of-memory errors.
      return;
    }
    byte a = (byte) 'a';
    byte c = (byte) 'c';

    int size = SEGMENT_SIZE * 5;
    byte[] bytes = new byte[size];
    Arrays.fill(bytes, a);

    // These are tricky places where the buffer
    // starts, ends, or segments come together.
    int[] points = {
        0,                       1,                   2,
        SEGMENT_SIZE - 1,        SEGMENT_SIZE,        SEGMENT_SIZE + 1,
        size / 2 - 1,            size / 2,            size / 2 + 1,
        size - SEGMENT_SIZE - 1, size - SEGMENT_SIZE, size - SEGMENT_SIZE + 1,
        size - 3,                size - 2,            size - 1
    };

    // In each iteration, we write c to the known point and then search for it using different
    // windows. Some of the windows don't overlap with c's position, and therefore a match shouldn't
    // be found.
    for (int p : points) {
      bytes[p] = c;
      sink.write(bytes);
      sink.emit();

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
    sink.emit();

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
    sink.emit();
    assertEquals(5, source.indexOf(ByteString.encodeUtf8("flop")));
    source.readUtf8(); // Clear stream.

    // Make sure we backtrack and resume searching after partial match.
    sink.writeUtf8("hi hi hi hey");
    sink.emit();
    assertEquals(3, source.indexOf(ByteString.encodeUtf8("hi hi hey")));
  }

  @Test public void indexOfByteStringAtSegmentBoundary() throws IOException {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE - 1));
    sink.writeUtf8("bcd");
    sink.emit();
    assertEquals(SEGMENT_SIZE - 3, source.indexOf(ByteString.encodeUtf8("aabc"), SEGMENT_SIZE - 4));
    assertEquals(SEGMENT_SIZE - 3, source.indexOf(ByteString.encodeUtf8("aabc"), SEGMENT_SIZE - 3));
    assertEquals(SEGMENT_SIZE - 2, source.indexOf(ByteString.encodeUtf8("abcd"), SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE - 2, source.indexOf(ByteString.encodeUtf8("abc"),  SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE - 2, source.indexOf(ByteString.encodeUtf8("abc"),  SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE - 2, source.indexOf(ByteString.encodeUtf8("ab"),   SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE - 2, source.indexOf(ByteString.encodeUtf8("a"),    SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE - 1, source.indexOf(ByteString.encodeUtf8("bc"),   SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE - 1, source.indexOf(ByteString.encodeUtf8("b"),    SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE,     source.indexOf(ByteString.encodeUtf8("c"),    SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE,     source.indexOf(ByteString.encodeUtf8("c"),    SEGMENT_SIZE    ));
    assertEquals(SEGMENT_SIZE + 1, source.indexOf(ByteString.encodeUtf8("d"),    SEGMENT_SIZE - 2));
    assertEquals(SEGMENT_SIZE + 1, source.indexOf(ByteString.encodeUtf8("d"),    SEGMENT_SIZE + 1));
  }

  @Test public void indexOfDoesNotWrapAround() throws IOException {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE - 1));
    sink.writeUtf8("bcd");
    sink.emit();
    assertEquals(-1, source.indexOf(ByteString.encodeUtf8("abcda"), SEGMENT_SIZE - 3));
  }

  @Test public void indexOfByteStringWithOffset() throws IOException {
    assertEquals(-1, source.indexOf(ByteString.encodeUtf8("flop"), 1));

    sink.writeUtf8("flop flip flop");
    sink.emit();
    assertEquals(10, source.indexOf(ByteString.encodeUtf8("flop"), 1));
    source.readUtf8(); // Clear stream

    // Make sure we backtrack and resume searching after partial match.
    sink.writeUtf8("hi hi hi hi hey");
    sink.emit();
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
      assertEquals("fromIndex < 0: -1", e.getMessage());
    }
  }

  /**
   * With {@link Factory#ONE_BYTE_AT_A_TIME_BUFFERED_SOURCE}, this code was extremely slow.
   * https://github.com/square/okio/issues/171
   */
  @Test public void indexOfByteStringAcrossSegmentBoundaries() throws IOException {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE * 2 - 3));
    sink.writeUtf8("bcdefg");
    sink.emit();
    assertEquals(SEGMENT_SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("ab")));
    assertEquals(SEGMENT_SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abc")));
    assertEquals(SEGMENT_SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abcd")));
    assertEquals(SEGMENT_SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abcde")));
    assertEquals(SEGMENT_SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abcdef")));
    assertEquals(SEGMENT_SIZE * 2 - 4, source.indexOf(ByteString.encodeUtf8("abcdefg")));
    assertEquals(SEGMENT_SIZE * 2 - 3, source.indexOf(ByteString.encodeUtf8("bcdefg")));
    assertEquals(SEGMENT_SIZE * 2 - 2, source.indexOf(ByteString.encodeUtf8("cdefg")));
    assertEquals(SEGMENT_SIZE * 2 - 1, source.indexOf(ByteString.encodeUtf8("defg")));
    assertEquals(SEGMENT_SIZE * 2,     source.indexOf(ByteString.encodeUtf8("efg")));
    assertEquals(SEGMENT_SIZE * 2 + 1, source.indexOf(ByteString.encodeUtf8("fg")));
    assertEquals(SEGMENT_SIZE * 2 + 2, source.indexOf(ByteString.encodeUtf8("g")));
  }

  @Test public void indexOfElement() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat("b", SEGMENT_SIZE)).writeUtf8("c");
    sink.emit();
    assertEquals(0, source.indexOfElement(ByteString.encodeUtf8("DEFGaHIJK")));
    assertEquals(1, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJKb")));
    assertEquals(SEGMENT_SIZE + 1, source.indexOfElement(ByteString.encodeUtf8("cDEFGHIJK")));
    assertEquals(1, source.indexOfElement(ByteString.encodeUtf8("DEFbGHIc")));
    assertEquals(-1L, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJK")));
    assertEquals(-1L, source.indexOfElement(ByteString.encodeUtf8("")));
  }

  @Test public void indexOfElementWithOffset() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat("b", SEGMENT_SIZE)).writeUtf8("c");
    sink.emit();
    assertEquals(-1, source.indexOfElement(ByteString.encodeUtf8("DEFGaHIJK"), 1));
    assertEquals(15, source.indexOfElement(ByteString.encodeUtf8("DEFGHIJKb"), 15));
  }

  @Test public void indexOfByteWithFromIndex() throws Exception {
    sink.writeUtf8("aaa");
    sink.emit();
    assertEquals(0, source.indexOf((byte) 'a'));
    assertEquals(0, source.indexOf((byte) 'a', 0));
    assertEquals(1, source.indexOf((byte) 'a', 1));
    assertEquals(2, source.indexOf((byte) 'a', 2));
  }

  @Test public void indexOfByteStringWithFromIndex() throws Exception {
    sink.writeUtf8("aaa");
    sink.emit();
    assertEquals(0, source.indexOf(ByteString.encodeUtf8("a")));
    assertEquals(0, source.indexOf(ByteString.encodeUtf8("a"), 0));
    assertEquals(1, source.indexOf(ByteString.encodeUtf8("a"), 1));
    assertEquals(2, source.indexOf(ByteString.encodeUtf8("a"), 2));
  }

  @Test public void indexOfElementWithFromIndex() throws Exception {
    sink.writeUtf8("aaa");
    sink.emit();
    assertEquals(0, source.indexOfElement(ByteString.encodeUtf8("a")));
    assertEquals(0, source.indexOfElement(ByteString.encodeUtf8("a"), 0));
    assertEquals(1, source.indexOfElement(ByteString.encodeUtf8("a"), 1));
    assertEquals(2, source.indexOfElement(ByteString.encodeUtf8("a"), 2));
  }

  @Test public void request() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat("b", SEGMENT_SIZE)).writeUtf8("c");
    sink.emit();
    assertTrue(source.request(SEGMENT_SIZE + 2));
    assertFalse(source.request(SEGMENT_SIZE + 3));
  }

  @Test public void require() throws IOException {
    sink.writeUtf8("a").writeUtf8(repeat("b", SEGMENT_SIZE)).writeUtf8("c");
    sink.emit();
    source.require(SEGMENT_SIZE + 2);
    try {
      source.require(SEGMENT_SIZE + 3);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void inputStream() throws Exception {
    sink.writeUtf8("abc");
    sink.emit();
    InputStream in = source.inputStream();
    byte[] bytes = { 'z', 'z', 'z' };
    int read = in.read(bytes);
    if (factory.isOneByteAtATime()) {
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
    sink.emit();
    InputStream in = source.inputStream();
    byte[] bytes = { 'z', 'z', 'z', 'z', 'z' };
    int read = in.read(bytes, 1, 3);
    if (factory.isOneByteAtATime()) {
      assertEquals(1, read);
      assertByteArrayEquals("zazzz", bytes);
    } else {
      assertEquals(3, read);
      assertByteArrayEquals("zabcz", bytes);
    }
  }

  @Test public void inputStreamSkip() throws Exception {
    sink.writeUtf8("abcde");
    sink.emit();
    InputStream in = source.inputStream();
    assertEquals(4, in.skip(4));
    assertEquals('e', in.read());

    sink.writeUtf8("abcde");
    sink.emit();
    assertEquals(5, in.skip(10)); // Try to skip too much.
    assertEquals(0, in.skip(1)); // Try to skip when exhausted.
  }

  @Test public void inputStreamCharByChar() throws Exception {
    sink.writeUtf8("abc");
    sink.emit();
    InputStream in = source.inputStream();
    assertEquals('a', in.read());
    assertEquals('b', in.read());
    assertEquals('c', in.read());
    assertEquals(-1, in.read());
  }

  @Test public void inputStreamBounds() throws IOException {
    sink.writeUtf8(repeat("a", 100));
    sink.emit();
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
    assertLongHexString(repeat("0", SEGMENT_SIZE + 1) + "1", 0x1);
  }

  private void assertLongHexString(String s, long expected) throws IOException {
    sink.writeUtf8(s);
    sink.emit();
    long actual = source.readHexadecimalUnsignedLong();
    assertEquals(s + " --> " + expected, expected, actual);
  }

  @Test public void longHexStringAcrossSegment() throws IOException {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE - 8)).writeUtf8("FFFFFFFFFFFFFFFF");
    sink.emit();
    source.skip(SEGMENT_SIZE - 8);
    assertEquals(-1, source.readHexadecimalUnsignedLong());
  }

  @Test public void longHexStringTooLongThrows() throws IOException {
    try {
      sink.writeUtf8("fffffffffffffffff");
      sink.emit();
      source.readHexadecimalUnsignedLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Number too large: fffffffffffffffff", e.getMessage());
    }
  }

  @Test public void longHexStringTooShortThrows() throws IOException {
    try {
      sink.writeUtf8(" ");
      sink.emit();
      source.readHexadecimalUnsignedLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Expected leading [0-9a-fA-F] character but was 0x20", e.getMessage());
    }
  }

  @Test public void longHexEmptySourceThrows() throws IOException {
    try {
      sink.writeUtf8("");
      sink.emit();
      source.readHexadecimalUnsignedLong();
      fail();
    } catch (EOFException expected) {
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
    sink.emit();
    long actual = source.readDecimalLong();
    assertEquals(s + " --> " + expected, expected, actual);
    assertEquals("zzz", source.readUtf8());
  }

  @Test public void longDecimalStringAcrossSegment() throws IOException {
    sink.writeUtf8(repeat("a", SEGMENT_SIZE - 8)).writeUtf8("1234567890123456");
    sink.writeUtf8("zzz");
    sink.emit();
    source.skip(SEGMENT_SIZE - 8);
    assertEquals(1234567890123456L, source.readDecimalLong());
    assertEquals("zzz", source.readUtf8());
  }

  @Test public void longDecimalStringTooLongThrows() throws IOException {
    try {
      sink.writeUtf8("12345678901234567890"); // Too many digits.
      sink.emit();
      source.readDecimalLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Number too large: 12345678901234567890", e.getMessage());
    }
  }

  @Test public void longDecimalStringTooHighThrows() throws IOException {
    try {
      sink.writeUtf8("9223372036854775808"); // Right size but cannot fit.
      sink.emit();
      source.readDecimalLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Number too large: 9223372036854775808", e.getMessage());
    }
  }

  @Test public void longDecimalStringTooLowThrows() throws IOException {
    try {
      sink.writeUtf8("-9223372036854775809"); // Right size but cannot fit.
      sink.emit();
      source.readDecimalLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Number too large: -9223372036854775809", e.getMessage());
    }
  }

  @Test public void longDecimalStringTooShortThrows() throws IOException {
    try {
      sink.writeUtf8(" ");
      sink.emit();
      source.readDecimalLong();
      fail();
    } catch (NumberFormatException e) {
      assertEquals("Expected leading [0-9] or '-' character but was 0x20", e.getMessage());
    }
  }

  @Test public void longDecimalEmptyThrows() throws IOException {
    try {
      sink.writeUtf8("");
      sink.emit();
      source.readDecimalLong();
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void codePoints() throws IOException {
    sink.write(ByteString.decodeHex("7f"));
    sink.emit();
    assertEquals(0x7f, source.readUtf8CodePoint());

    sink.write(ByteString.decodeHex("dfbf"));
    sink.emit();
    assertEquals(0x07ff, source.readUtf8CodePoint());

    sink.write(ByteString.decodeHex("efbfbf"));
    sink.emit();
    assertEquals(0xffff, source.readUtf8CodePoint());

    sink.write(ByteString.decodeHex("f48fbfbf"));
    sink.emit();
    assertEquals(0x10ffff, source.readUtf8CodePoint());
  }

  @Test public void decimalStringWithManyLeadingZeros() throws IOException {
    assertLongDecimalString("00000000000000001", 1);
    assertLongDecimalString("00000000000000009223372036854775807", 9223372036854775807L);
    assertLongDecimalString("-00000000000000009223372036854775808", -9223372036854775808L);
    assertLongDecimalString(repeat("0", SEGMENT_SIZE + 1) + "1", 1);
  }

  @Test public void select() throws IOException {
    Options options = Options.Companion.of(
        ByteString.encodeUtf8("ROCK"),
        ByteString.encodeUtf8("SCISSORS"),
        ByteString.encodeUtf8("PAPER"));

    sink.writeUtf8("PAPER,SCISSORS,ROCK");
    sink.emit();
    assertEquals(2, source.select(options));
    assertEquals(',', source.readByte());
    assertEquals(1, source.select(options));
    assertEquals(',', source.readByte());
    assertEquals(0, source.select(options));
    assertTrue(source.exhausted());
  }

  @Test public void selectSpanningMultipleSegments() throws IOException {
    ByteString commonPrefix = TestUtil.randomBytes(SEGMENT_SIZE + 10);
    ByteString a = new Buffer().write(commonPrefix).writeUtf8("a").readByteString();
    ByteString bc = new Buffer().write(commonPrefix).writeUtf8("bc").readByteString();
    ByteString bd = new Buffer().write(commonPrefix).writeUtf8("bd").readByteString();
    Options options = Options.Companion.of(a, bc, bd);

    sink.write(bd);
    sink.write(a);
    sink.write(bc);
    sink.emit();

    assertEquals(2, source.select(options));
    assertEquals(0, source.select(options));
    assertEquals(1, source.select(options));
    assertTrue(source.exhausted());
  }

  @Test public void selectNotFound() throws IOException {
    Options options = Options.Companion.of(
        ByteString.encodeUtf8("ROCK"),
        ByteString.encodeUtf8("SCISSORS"),
        ByteString.encodeUtf8("PAPER"));

    sink.writeUtf8("SPOCK");
    sink.emit();
    assertEquals(-1, source.select(options));
    assertEquals("SPOCK", source.readUtf8());
  }

  @Test public void selectValuesHaveCommonPrefix() throws IOException {
    Options options = Options.Companion.of(
        ByteString.encodeUtf8("abcd"),
        ByteString.encodeUtf8("abce"),
        ByteString.encodeUtf8("abcc"));

    sink.writeUtf8("abcc").writeUtf8("abcd").writeUtf8("abce");
    sink.emit();
    assertEquals(2, source.select(options));
    assertEquals(0, source.select(options));
    assertEquals(1, source.select(options));
  }

  @Test public void selectLongerThanSource() throws IOException {
    Options options = Options.Companion.of(
        ByteString.encodeUtf8("abcd"),
        ByteString.encodeUtf8("abce"),
        ByteString.encodeUtf8("abcc"));
    sink.writeUtf8("abc");
    sink.emit();
    assertEquals(-1, source.select(options));
    assertEquals("abc", source.readUtf8());
  }

  @Test public void selectReturnsFirstByteStringThatMatches() throws IOException {
    Options options = Options.Companion.of(
        ByteString.encodeUtf8("abcd"),
        ByteString.encodeUtf8("abc"),
        ByteString.encodeUtf8("abcde"));
    sink.writeUtf8("abcdef");
    sink.emit();
    assertEquals(0, source.select(options));
    assertEquals("ef", source.readUtf8());
  }

  @Test public void selectFromEmptySource() throws IOException {
    Options options = Options.Companion.of(
        ByteString.encodeUtf8("abc"),
        ByteString.encodeUtf8("def"));
    assertEquals(-1, source.select(options));
  }

  @Test public void selectNoByteStringsFromEmptySource() throws IOException {
    Options options = Options.of();
    assertEquals(-1, source.select(options));
  }

  @Test public void peek() throws IOException {
    sink.writeUtf8("abcdefghi");
    sink.emit();

    assertEquals("abc", source.readUtf8(3));

    BufferedSource peek = source.peek();
    assertEquals("def", peek.readUtf8(3));
    assertEquals("ghi", peek.readUtf8(3));
    assertFalse(peek.request(1));

    assertEquals("def", source.readUtf8(3));
  }

  @Test public void peekMultiple() throws IOException {
    sink.writeUtf8("abcdefghi");
    sink.emit();

    assertEquals("abc", source.readUtf8(3));

    BufferedSource peek1 = source.peek();
    BufferedSource peek2 = source.peek();

    assertEquals("def", peek1.readUtf8(3));

    assertEquals("def", peek2.readUtf8(3));
    assertEquals("ghi", peek2.readUtf8(3));
    assertFalse(peek2.request(1));

    assertEquals("ghi", peek1.readUtf8(3));
    assertFalse(peek1.request(1));

    assertEquals("def", source.readUtf8(3));
  }

  @Test public void peekLarge() throws IOException {
    sink.writeUtf8("abcdef");
    sink.writeUtf8(repeat("g", 2 * SEGMENT_SIZE));
    sink.writeUtf8("hij");
    sink.emit();

    assertEquals("abc", source.readUtf8(3));

    BufferedSource peek = source.peek();
    assertEquals("def", peek.readUtf8(3));
    peek.skip(2 * SEGMENT_SIZE);
    assertEquals("hij", peek.readUtf8(3));
    assertFalse(peek.request(1));

    assertEquals("def", source.readUtf8(3));
    source.skip(2 * SEGMENT_SIZE);
    assertEquals("hij", source.readUtf8(3));
  }

  @Test public void peekInvalid() throws IOException {
    sink.writeUtf8("abcdefghi");
    sink.emit();

    assertEquals("abc", source.readUtf8(3));

    BufferedSource peek = source.peek();
    assertEquals("def", peek.readUtf8(3));
    assertEquals("ghi", peek.readUtf8(3));
    assertFalse(peek.request(1));

    assertEquals("def", source.readUtf8(3));

    try {
      peek.readUtf8();
      fail();
    } catch (IllegalStateException e) {
      assertEquals("Peek source is invalid because upstream source was used", e.getMessage());
    }
  }

  @Test public void peekSegmentThenInvalid() throws IOException {
    sink.writeUtf8("abc");
    sink.writeUtf8(repeat("d", 2 * SEGMENT_SIZE));
    sink.emit();

    assertEquals("abc", source.readUtf8(3));

    // Peek a little data and skip the rest of the upstream source
    BufferedSource peek = source.peek();
    assertEquals("ddd", peek.readUtf8(3));
    source.readAll(Okio.blackhole());

    // Skip the rest of the buffered data
    peek.skip(peek.getBuffer().size());

    try {
      peek.readByte();
      fail();
    } catch (IllegalStateException e) {
      assertEquals("Peek source is invalid because upstream source was used", e.getMessage());
    }
  }

  @Test public void peekDoesntReadTooMuch() throws IOException {
    // 6 bytes in source's buffer plus 3 bytes upstream.
    sink.writeUtf8("abcdef");
    sink.emit();
    source.require(6L);
    sink.writeUtf8("ghi");
    sink.emit();

    BufferedSource peek = source.peek();

    // Read 3 bytes. This reads some of the buffered data.
    assertTrue(peek.request(3));
    if (!(source instanceof Buffer)) {
      assertEquals(6, source.getBuffer().size());
      assertEquals(6, peek.getBuffer().size());
    }
    assertEquals("abc", peek.readUtf8(3L));

    // Read 3 more bytes. This exhausts the buffered data.
    assertTrue(peek.request(3));
    if (!(source instanceof Buffer)) {
      assertEquals(6, source.getBuffer().size());
      assertEquals(3, peek.getBuffer().size());
    }
    assertEquals("def", peek.readUtf8(3L));

    // Read 3 more bytes. This draws new bytes.
    assertTrue(peek.request(3));
    assertEquals(9, source.getBuffer().size());
    assertEquals(3, peek.getBuffer().size());
    assertEquals("ghi", peek.readUtf8(3L));
  }

  @Test public void rangeEquals() throws IOException {
    sink.writeUtf8("A man, a plan, a canal. Panama.");
    sink.emit();
    assertTrue(source.rangeEquals(7 , ByteString.encodeUtf8("a plan")));
    assertTrue(source.rangeEquals(0 , ByteString.encodeUtf8("A man")));
    assertTrue(source.rangeEquals(24, ByteString.encodeUtf8("Panama")));
    assertFalse(source.rangeEquals(24, ByteString.encodeUtf8("Panama. Panama. Panama.")));
  }

  @Test public void rangeEqualsWithOffsetAndCount() throws IOException {
    sink.writeUtf8("A man, a plan, a canal. Panama.");
    sink.emit();
    assertTrue(source.rangeEquals(7 , ByteString.encodeUtf8("aaa plannn"), 2, 6));
    assertTrue(source.rangeEquals(0 , ByteString.encodeUtf8("AAA mannn"), 2, 5));
    assertTrue(source.rangeEquals(24, ByteString.encodeUtf8("PPPanamaaa"), 2, 6));
  }

  @Test public void rangeEqualsOnlyReadsUntilMismatch() throws IOException {
    assumeTrue(factory == Factory.ONE_BYTE_AT_A_TIME_BUFFERED_SOURCE); // Other sources read in chunks anyway.

    sink.writeUtf8("A man, a plan, a canal. Panama.");
    sink.emit();
    assertFalse(source.rangeEquals(0, ByteString.encodeUtf8("A man.")));
    assertEquals("A man,", source.getBuffer().readUtf8());
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

  @Test public void readNioBuffer() throws Exception {
    String expected = factory.isOneByteAtATime() ? "a" : "abcdefg";
    sink.writeUtf8("abcdefg");
    sink.emit();

    ByteBuffer nioByteBuffer = ByteBuffer.allocate(1024);
    int byteCount = source.read(nioByteBuffer);
    assertEquals(expected.length(), byteCount);
    assertEquals(expected.length(), nioByteBuffer.position());
    assertEquals(nioByteBuffer.capacity(), nioByteBuffer.limit());

    nioByteBuffer.flip();
    byte[] data = new byte[expected.length()];
    nioByteBuffer.get(data);
    assertEquals(expected, new String(data));
  }

  @Test public void readLargeNioBufferOnlyReadsOneSegment() throws Exception {
    String expected = factory.isOneByteAtATime()
        ? "a"
        : repeat("a", SEGMENT_SIZE);
    sink.writeUtf8(repeat("a", SEGMENT_SIZE * 4));
    sink.emit();

    ByteBuffer nioByteBuffer = ByteBuffer.allocate(SEGMENT_SIZE * 3);
    int byteCount = source.read(nioByteBuffer);
    assertEquals(expected.length(), byteCount);
    assertEquals(expected.length(), nioByteBuffer.position());
    assertEquals(nioByteBuffer.capacity(), nioByteBuffer.limit());

    nioByteBuffer.flip();
    byte[] data = new byte[expected.length()];
    nioByteBuffer.get(data);
    assertEquals(expected, new String(data));
  }

  @Test public void factorySegmentSizes() throws Exception {
    sink.writeUtf8("abc");
    sink.emit();
    source.require(3);
    if (factory.isOneByteAtATime()) {
      assertEquals(Arrays.asList(1, 1, 1), TestUtil.segmentSizes(source.getBuffer()));
    } else {
      assertEquals(Collections.singletonList(3), TestUtil.segmentSizes(source.getBuffer()));
    }
  }
}
