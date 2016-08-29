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
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static java.util.Arrays.asList;
import static okio.TestUtil.repeat;
import static okio.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public final class BufferedSinkTest {
  private interface Factory {
    Factory BUFFER = new Factory() {
      @Override public BufferedSink create(Buffer data) {
        return data;
      }

      @Override public String toString() {
        return "Buffer";
      }
    };

    Factory REAL_BUFFERED_SINK = new Factory() {
      @Override public BufferedSink create(Buffer data) {
        return new RealBufferedSink(data);
      }

      @Override public String toString() {
        return "RealBufferedSink";
      }
    };

    BufferedSink create(Buffer data);
  }

  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return Arrays.asList(
        new Object[] {Factory.BUFFER},
        new Object[] {Factory.REAL_BUFFERED_SINK});
  }

  @Parameter public Factory factory;
  private Buffer data;
  private BufferedSink sink;

  @Before public void setUp() {
    data = new Buffer();
    sink = factory.create(data);
  }

  @Test public void writeNothing() throws IOException {
    sink.writeUtf8("");
    sink.flush();
    assertEquals(0, data.size());
  }

  @Test public void writeBytes() throws Exception {
    sink.writeByte(0xab);
    sink.writeByte(0xcd);
    sink.flush();
    assertEquals("[hex=abcd]", data.toString());
  }

  @Test public void writeLastByteInSegment() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE - 1));
    sink.writeByte(0x20);
    sink.writeByte(0x21);
    sink.flush();
    assertEquals(asList(Segment.SIZE, 1), data.segmentSizes());
    assertEquals(repeat('a', Segment.SIZE - 1), data.readUtf8(Segment.SIZE - 1));
    assertEquals("[text= !]", data.toString());
  }

  @Test public void writeShort() throws Exception {
    sink.writeShort(0xabcd);
    sink.writeShort(0x4321);
    sink.flush();
    assertEquals("[hex=abcd4321]", data.toString());
  }

  @Test public void writeShortLe() throws Exception {
    sink.writeShortLe(0xcdab);
    sink.writeShortLe(0x2143);
    sink.flush();
    assertEquals("[hex=abcd4321]", data.toString());
  }

  @Test public void writeInt() throws Exception {
    sink.writeInt(0xabcdef01);
    sink.writeInt(0x87654321);
    sink.flush();
    assertEquals("[hex=abcdef0187654321]", data.toString());
  }

  @Test public void writeLastIntegerInSegment() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE - 4));
    sink.writeInt(0xabcdef01);
    sink.writeInt(0x87654321);
    sink.flush();
    assertEquals(asList(Segment.SIZE, 4), data.segmentSizes());
    assertEquals(repeat('a', Segment.SIZE - 4), data.readUtf8(Segment.SIZE - 4));
    assertEquals("[hex=abcdef0187654321]", data.toString());
  }

  @Test public void writeIntegerDoesNotQuiteFitInSegment() throws Exception {
    sink.writeUtf8(repeat('a', Segment.SIZE - 3));
    sink.writeInt(0xabcdef01);
    sink.writeInt(0x87654321);
    sink.flush();
    assertEquals(asList(Segment.SIZE - 3, 8), data.segmentSizes());
    assertEquals(repeat('a', Segment.SIZE - 3), data.readUtf8(Segment.SIZE - 3));
    assertEquals("[hex=abcdef0187654321]", data.toString());
  }

  @Test public void writeIntLe() throws Exception {
    sink.writeIntLe(0xabcdef01);
    sink.writeIntLe(0x87654321);
    sink.flush();
    assertEquals("[hex=01efcdab21436587]", data.toString());
  }

  @Test public void writeLong() throws Exception {
    sink.writeLong(0xabcdef0187654321L);
    sink.writeLong(0xcafebabeb0b15c00L);
    sink.flush();
    assertEquals("[hex=abcdef0187654321cafebabeb0b15c00]", data.toString());
  }

  @Test public void writeLongLe() throws Exception {
    sink.writeLongLe(0xabcdef0187654321L);
    sink.writeLongLe(0xcafebabeb0b15c00L);
    sink.flush();
    assertEquals("[hex=2143658701efcdab005cb1b0bebafeca]", data.toString());
  }

  @Test public void writeStringUtf8() throws IOException {
    sink.writeUtf8("təˈranəˌsôr");
    sink.flush();
    assertEquals(ByteString.decodeHex("74c999cb8872616ec999cb8c73c3b472"), data.readByteString());
  }

  @Test public void writeSubstringUtf8() throws IOException {
    sink.writeUtf8("təˈranəˌsôr", 3, 7);
    sink.flush();
    assertEquals(ByteString.decodeHex("72616ec999"), data.readByteString());
  }

  @Test public void writeStringWithCharset() throws IOException {
    sink.writeString("təˈranəˌsôr", Charset.forName("utf-32be"));
    sink.flush();
    assertEquals(ByteString.decodeHex("0000007400000259000002c800000072000000610000006e00000259"
        + "000002cc00000073000000f400000072"), data.readByteString());
  }

  @Test public void writeSubstringWithCharset() throws IOException {
    sink.writeString("təˈranəˌsôr", 3, 7, Charset.forName("utf-32be"));
    sink.flush();
    assertEquals(ByteString.decodeHex("00000072000000610000006e00000259"), data.readByteString());
  }

  @Test public void writeUtf8SubstringWithCharset() throws IOException {
    sink.writeString("təˈranəˌsôr", 3, 7, Charset.forName("utf-8"));
    sink.flush();
    assertEquals(ByteString.encodeUtf8("ranə"), data.readByteString());
  }

  @Test public void writeAll() throws Exception {
    Buffer source = new Buffer().writeUtf8("abcdef");

    assertEquals(6, sink.writeAll(source));
    assertEquals(0, source.size());
    sink.flush();
    assertEquals("abcdef", data.readUtf8());
  }

  @Test public void writeSource() throws Exception {
    Buffer source = new Buffer().writeUtf8("abcdef");

    // Force resolution of the Source method overload.
    sink.write((Source) source, 4);
    sink.flush();
    assertEquals("abcd", data.readUtf8());
    assertEquals("ef", source.readUtf8());
  }

  @Test public void writeSourceReadsFully() throws Exception {
    Source source = new ForwardingSource(new Buffer()) {
      @Override public long read(Buffer sink, long byteCount) throws IOException {
        sink.writeUtf8("abcd");
        return 4;
      }
    };

    sink.write(source, 8);
    sink.flush();
    assertEquals("abcdabcd", data.readUtf8());
  }

  @Test public void writeSourcePropagatesEof() throws IOException {
    Source source = new Buffer().writeUtf8("abcd");

    try {
      sink.write(source, 8);
      fail();
    } catch (EOFException expected) {
    }

    // Ensure that whatever was available was correctly written.
    sink.flush();
    assertEquals("abcd", data.readUtf8());
  }

  @Test public void writeSourceWithZeroIsNoOp() throws IOException {
    // This test ensures that a zero byte count never calls through to read the source. It may be
    // tied to something like a socket which will potentially block trying to read a segment when
    // ultimately we don't want any data.
    Source source = new ForwardingSource(new Buffer()) {
      @Override public long read(Buffer sink, long byteCount) throws IOException {
        throw new AssertionError();
      }
    };
    sink.write(source, 0);
    assertEquals(0, data.size());
  }

  @Test public void writeAllExhausted() throws Exception {
    Buffer source = new Buffer();
    assertEquals(0, sink.writeAll(source));
    assertEquals(0, source.size());
  }

  @Test public void closeEmitsBufferedBytes() throws IOException {
    sink.writeByte('a');
    sink.close();
    assertEquals('a', data.readByte());
  }

  @Test public void outputStream() throws Exception {
    OutputStream out = sink.outputStream();
    out.write('a');
    out.write(repeat('b', 9998).getBytes(UTF_8));
    out.write('c');
    out.flush();
    assertEquals("a" + repeat('b', 9998) + "c", data.readUtf8());
  }

  @Test public void outputStreamBounds() throws Exception {
    OutputStream out = sink.outputStream();
    try {
      out.write(new byte[100], 50, 51);
      fail();
    } catch (ArrayIndexOutOfBoundsException expected) {
    }
  }

  @Test public void longDecimalString() throws IOException {
    assertLongDecimalString(0);
    assertLongDecimalString(Long.MIN_VALUE);
    assertLongDecimalString(Long.MAX_VALUE);

    for (int i = 1; i < 20; i++) {
      long value = BigInteger.valueOf(10L).pow(i).longValue();
      assertLongDecimalString(value - 1);
      assertLongDecimalString(value);
    }
  }

  private void assertLongDecimalString(long value) throws IOException {
    sink.writeDecimalLong(value).writeUtf8("zzz").flush();
    String expected = Long.toString(value) + "zzz";
    String actual = data.readUtf8();
    assertEquals(value + " expected " + expected + " but was " + actual, actual, expected);
  }

  @Test public void longHexString() throws IOException {
    assertLongHexString(0);
    assertLongHexString(Long.MIN_VALUE);
    assertLongHexString(Long.MAX_VALUE);

    for (int i = 0; i < 16; i++) {
      assertLongHexString((1 << i) - 1);
      assertLongHexString(1 << i);
    }
  }

  private void assertLongHexString(long value) throws IOException {
    sink.writeHexadecimalUnsignedLong(value).writeUtf8("zzz").flush();
    String expected = String.format("%x", value) + "zzz";
    String actual = data.readUtf8();
    assertEquals(value + " expected " + expected + " but was " + actual, actual, expected);
  }
}
