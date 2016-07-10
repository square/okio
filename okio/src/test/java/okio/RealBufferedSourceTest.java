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
import java.io.Reader;
import java.nio.charset.Charset;
import org.junit.Test;

import static okio.TestUtil.readerToString;
import static okio.TestUtil.repeat;
import static okio.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests solely for the behavior of RealBufferedSource's implementation. For generic
 * BufferedSource behavior use BufferedSourceTest.
 */
public final class RealBufferedSourceTest {
  @Test public void inputStreamTracksSegments() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("a");
    source.writeUtf8(repeat('b', Segment.SIZE));
    source.writeUtf8("c");

    InputStream in = new RealBufferedSource(source).inputStream();
    assertEquals(0, in.available());
    assertEquals(Segment.SIZE + 2, source.size());

    // Reading one byte buffers a full segment.
    assertEquals('a', in.read());
    assertEquals(Segment.SIZE - 1, in.available());
    assertEquals(2, source.size());

    // Reading as much as possible reads the rest of that buffered segment.
    byte[] data = new byte[Segment.SIZE * 2];
    assertEquals(Segment.SIZE - 1, in.read(data, 0, data.length));
    assertEquals(repeat('b', Segment.SIZE - 1), new String(data, 0, Segment.SIZE - 1, UTF_8));
    assertEquals(2, source.size());

    // Continuing to read buffers the next segment.
    assertEquals('b', in.read());
    assertEquals(1, in.available());
    assertEquals(0, source.size());

    // Continuing to read reads from the buffer.
    assertEquals('c', in.read());
    assertEquals(0, in.available());
    assertEquals(0, source.size());

    // Once we've exhausted the source, we're done.
    assertEquals(-1, in.read());
    assertEquals(0, source.size());
  }

  @Test public void inputStreamCloses() throws Exception {
    RealBufferedSource source = new RealBufferedSource(new Buffer());
    InputStream in = source.inputStream();
    in.close();
    try {
      source.require(1);
      fail();
    } catch (IllegalStateException e) {
      assertEquals("closed", e.getMessage());
    }
  }

  @Test public void requireTracksBufferFirst() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("bb");

    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.buffer().writeUtf8("aa");

    bufferedSource.require(2);
    assertEquals(2, bufferedSource.buffer().size());
    assertEquals(2, source.size());
  }

  @Test public void requireIncludesBufferBytes() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("b");

    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.buffer().writeUtf8("a");

    bufferedSource.require(2);
    assertEquals("ab", bufferedSource.buffer().readUtf8(2));
  }

  @Test public void requireInsufficientData() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("a");

    BufferedSource bufferedSource = new RealBufferedSource(source);

    try {
      bufferedSource.require(2);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void requireReadsOneSegmentAtATime() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8(repeat('a', Segment.SIZE));
    source.writeUtf8(repeat('b', Segment.SIZE));

    BufferedSource bufferedSource = new RealBufferedSource(source);

    bufferedSource.require(2);
    assertEquals(Segment.SIZE, source.size());
    assertEquals(Segment.SIZE, bufferedSource.buffer().size());
  }

  @Test public void skipReadsOneSegmentAtATime() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8(repeat('a', Segment.SIZE));
    source.writeUtf8(repeat('b', Segment.SIZE));
    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.skip(2);
    assertEquals(Segment.SIZE, source.size());
    assertEquals(Segment.SIZE - 2, bufferedSource.buffer().size());
  }

  @Test public void skipTracksBufferFirst() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("bb");

    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.buffer().writeUtf8("aa");

    bufferedSource.skip(2);
    assertEquals(0, bufferedSource.buffer().size());
    assertEquals(2, source.size());
  }

  @Test public void operationsAfterClose() throws IOException {
    Buffer source = new Buffer();
    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.close();

    // Test a sample set of methods.
    try {
      bufferedSource.indexOf((byte) 1);
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSource.skip(1);
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSource.readByte();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSource.readByteString(10);
      fail();
    } catch (IllegalStateException expected) {
    }

    // Test a sample set of methods on the InputStream.
    InputStream is = bufferedSource.inputStream();
    try {
      is.read();
      fail();
    } catch (IOException expected) {
    }

    try {
      is.read(new byte[10]);
      fail();
    } catch (IOException expected) {
    }

    Reader reader = bufferedSource.reader();
    try {
      reader.read();
      fail();
    } catch (IOException expected) {
    }

    Reader readerWithCharset = bufferedSource.reader(UTF_8);
    try {
      readerWithCharset.read();
      fail();
    } catch (IOException expected) {
    }
  }

  /**
   * We don't want readAll to buffer an unbounded amount of data. Instead it
   * should buffer a segment, write it, and repeat.
   */
  @Test public void readAllReadsOneSegmentAtATime() throws IOException {
    Buffer write1 = new Buffer().writeUtf8(TestUtil.repeat('a', Segment.SIZE));
    Buffer write2 = new Buffer().writeUtf8(TestUtil.repeat('b', Segment.SIZE));
    Buffer write3 = new Buffer().writeUtf8(TestUtil.repeat('c', Segment.SIZE));

    Buffer source = new Buffer().writeUtf8(""
        + TestUtil.repeat('a', Segment.SIZE)
        + TestUtil.repeat('b', Segment.SIZE)
        + TestUtil.repeat('c', Segment.SIZE));

    MockSink mockSink = new MockSink();
    BufferedSource bufferedSource = Okio.buffer((Source) source);
    assertEquals(Segment.SIZE * 3, bufferedSource.readAll(mockSink));
    mockSink.assertLog(
        "write(" + write1 + ", " + write1.size() + ")",
        "write(" + write2 + ", " + write2.size() + ")",
        "write(" + write3 + ", " + write3.size() + ")");
  }

  @Test public void readerWithoutBom() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("ab");
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader();
    String stringFromReader = readerToString(reader);

    assertEquals("ab", stringFromReader);
  }

  @Test public void readerWithUtf8Bom() throws Exception {
    Buffer source = new Buffer();
    source.write(ByteString.decodeHex("efbbbf"));
    source.writeUtf8("utf8");
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader();
    String stringFromReader = readerToString(reader);

    assertEquals("utf8", stringFromReader);
  }

  @Test public void readerWithUtf16BigEndianBom() throws Exception {
    Buffer source = new Buffer();
    source.write(ByteString.decodeHex("feff"));
    source.writeString("utf16", Charset.forName("UTF-16BE"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader();
    String stringFromReader = readerToString(reader);

    assertEquals("utf16", stringFromReader);
  }

  @Test public void readerWithUtf16LittleEndianBom() throws Exception {
    Buffer source = new Buffer();
    source.write(ByteString.decodeHex("fffe"));
    source.writeString("utf16", Charset.forName("UTF-16LE"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader();
    String stringFromReader = readerToString(reader);

    assertEquals("utf16", stringFromReader);
  }

  @Test public void readerWithUtf32BigEndianBom() throws Exception {
    Buffer source = new Buffer();
    source.write(ByteString.decodeHex("0000feff"));
    source.writeString("utf32", Charset.forName("UTF-32BE"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader();
    String stringFromReader = readerToString(reader);

    assertEquals("utf32", stringFromReader);
  }

  @Test public void readerWithUtf32LittleEndianBom() throws Exception {
    Buffer source = new Buffer();
    source.write(ByteString.decodeHex("fffe0000"));
    source.writeString("utf32", Charset.forName("UTF-32LE"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader();
    String string = readerToString(reader);

    assertEquals("utf32", string);
  }

  @Test public void readerWithBomCrossingSegmentBoundary() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8(TestUtil.repeat('a', Segment.SIZE - 1));
    source.write(ByteString.decodeHex("efbbbf"));
    source.writeUtf8("utf8");
    BufferedSource bufferedSource = new RealBufferedSource(source);

    bufferedSource.readByteString(Segment.SIZE - 1);
    Reader reader = bufferedSource.reader();
    String stringFromReader = readerToString(reader);

    assertEquals("utf8", stringFromReader);
  }

  @Test public void readerUtf8() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("utf8");
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader(UTF_8);
    String stringFromReader = readerToString(reader);

    assertEquals("utf8", stringFromReader);
  }

  @Test public void readerUtf8WithBom() throws Exception {
    Buffer source = new Buffer();
    source.write(ByteString.decodeHex("efbbbf"));
    source.writeUtf8("utf8");
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader(UTF_8);
    String stringFromReader = readerToString(reader);

    assertEquals("utf8", stringFromReader);
  }

  @Test public void readerUtf16() throws Exception {
    Buffer source = new Buffer();
    source.writeString("utf16", Charset.forName("UTF-16BE"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader(Charset.forName("UTF-16BE"));
    String stringFromReader = readerToString(reader);

    assertEquals("utf16", stringFromReader);
  }

  @Test public void readerUtf16WithBom() throws Exception {
    Buffer source = new Buffer();
    source.write(ByteString.decodeHex("feff"));
    source.writeString("utf16", Charset.forName("UTF-16BE"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader(Charset.forName("UTF-16"));
    String stringFromReader = readerToString(reader);

    assertEquals("utf16", stringFromReader);
  }

  @Test public void readerUtf32() throws Exception {
    Buffer source = new Buffer();
    source.writeString("utf32", Charset.forName("UTF-32LE"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader(Charset.forName("UTF-32LE"));
    String stringFromReader = readerToString(reader);

    assertEquals("utf32", stringFromReader);
  }

  @Test public void readerUtf32WithBom() throws Exception {
    Buffer source = new Buffer();
    source.write(ByteString.decodeHex("fffe0000"));
    source.writeString("utf32", Charset.forName("UTF-32LE"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader(Charset.forName("UTF-32"));
    String stringFromReader = readerToString(reader);

    assertEquals("utf32", stringFromReader);
  }

  @Test public void readerLatinOne() throws Exception {
    Buffer source = new Buffer();
    source.writeString("Übergrößenträger", Charset.forName("ISO-8859-1"));
    BufferedSource bufferedSource = new RealBufferedSource(source);

    Reader reader = bufferedSource.reader(Charset.forName("ISO-8859-1"));
    String stringFromReader = readerToString(reader);

    assertEquals("Übergrößenträger", stringFromReader);
  }

  @Test public void readerCloses() throws Exception {
    RealBufferedSource source = new RealBufferedSource(new Buffer());
    Reader reader = source.reader();
    reader.close();
    try {
      source.require(1);
      fail();
    } catch (IllegalStateException e) {
      assertEquals("closed", e.getMessage());
    }
  }
}
