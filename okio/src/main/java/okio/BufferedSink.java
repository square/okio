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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * A sink that keeps a buffer internally so that callers can do small writes
 * without a performance penalty.
 */
public interface BufferedSink extends Sink {
  /** Returns this sink's internal buffer. */
  Buffer buffer();

  BufferedSink write(ByteString byteString) throws IOException;

  /**
   * Like {@link OutputStream#write(byte[])}, this writes a complete byte array to
   * this sink.
   */
  BufferedSink write(byte[] source) throws IOException;

  /**
   * Like {@link OutputStream#write(byte[], int, int)}, this writes {@code byteCount}
   * bytes of {@code source}, starting at {@code offset}.
   */
  BufferedSink write(byte[] source, int offset, int byteCount) throws IOException;

  /**
   * Removes all bytes from {@code source} and appends them to this sink. Returns the
   * number of bytes read which will be 0 if {@code source} is exhausted.
   */
  long writeAll(Source source) throws IOException;

  /** Removes {@code byteCount} bytes from {@code source} and appends them to this sink. */
  BufferedSink write(Source source, long byteCount) throws IOException;

  /**
   * Encodes {@code string} in UTF-8 and writes it to this sink. <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeUtf8("Uh uh uh!");
   *   buffer.writeByte(' ');
   *   buffer.writeUtf8("You didn't say the magic word!");
   *
   *   assertEquals("Uh uh uh! You didn't say the magic word!", buffer.readUtf8());
   * }</pre>
   */
  BufferedSink writeUtf8(String string) throws IOException;

  /**
   * Encodes the characters at {@code beginIndex} up to {@code endIndex} from {@code string} in
   * UTF-8 and writes it to this sink. <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeUtf8("I'm a hacker!\n", 6, 12);
   *   buffer.writeByte(' ');
   *   buffer.writeUtf8("That's what I said: you're a nerd.\n", 29, 33);
   *   buffer.writeByte(' ');
   *   buffer.writeUtf8("I prefer to be called a hacker!\n", 24, 31);
   *
   *   assertEquals("hacker nerd hacker!", buffer.readUtf8());
   * }</pre>
   */
  BufferedSink writeUtf8(String string, int beginIndex, int endIndex) throws IOException;

  /** Encodes {@code codePoint} in UTF-8 and writes it to this sink. */
  BufferedSink writeUtf8CodePoint(int codePoint) throws IOException;

  /** Encodes {@code string} in {@code charset} and writes it to this sink. */
  BufferedSink writeString(String string, Charset charset) throws IOException;

  /**
   * Encodes the characters at {@code beginIndex} up to {@code endIndex} from {@code string} in
   * {@code charset} and writes it to this sink.
   */
  BufferedSink writeString(String string, int beginIndex, int endIndex, Charset charset)
      throws IOException;

  /** Writes a byte to this sink. */
  BufferedSink writeByte(int b) throws IOException;

  /**
   * Writes a big-endian short to this sink using two bytes. <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeShort(32767);
   *   buffer.writeShort(15);
   *
   *   assertEquals(4, buffer.size());
   *   assertEquals((byte) 0x7f, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x0f, buffer.readByte());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  BufferedSink writeShort(int s) throws IOException;

  /**
   * Writes a little-endian short to this sink using two bytes. <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeShortLe(32767);
   *   buffer.writeShortLe(15);
   *
   *   assertEquals(4, buffer.size());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0x7f, buffer.readByte());
   *   assertEquals((byte) 0x0f, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  BufferedSink writeShortLe(int s) throws IOException;

  /**
   * Writes a big-endian int to this sink using four bytes. <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeInt(2147483647);
   *   buffer.writeInt(15);
   *
   *   assertEquals(8, buffer.size());
   *   assertEquals((byte) 0x7f, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x0f, buffer.readByte());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  BufferedSink writeInt(int i) throws IOException;

  /**
   * Writes a little-endian int to this sink using four bytes.  <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeIntLe(2147483647);
   *   buffer.writeIntLe(15);
   *
   *   assertEquals(8, buffer.size());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0x7f, buffer.readByte());
   *   assertEquals((byte) 0x0f, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  BufferedSink writeIntLe(int i) throws IOException;

  /**
   * Writes a big-endian long to this sink using eight bytes. <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeLong(9223372036854775807L);
   *   buffer.writeLong(15);
   *
   *   assertEquals(16, buffer.size());
   *   assertEquals((byte) 0x7f, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x0f, buffer.readByte());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  BufferedSink writeLong(long v) throws IOException;

  /**
   * Writes a little-endian long to this sink using eight bytes. <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeLongLe(9223372036854775807L);
   *   buffer.writeLongLe(15);
   *
   *   assertEquals(16, buffer.size());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0xff, buffer.readByte());
   *   assertEquals((byte) 0x7f, buffer.readByte());
   *   assertEquals((byte) 0x0f, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals((byte) 0x00, buffer.readByte());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  BufferedSink writeLongLe(long v) throws IOException;

  /**
   * Writes a long to this sink in signed decimal form (i.e., as a string in base 10). <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeDecimalLong(8675309L);
   *   buffer.writeByte(' ');
   *   buffer.writeDecimalLong(-123L);
   *   buffer.writeByte(' ');
   *   buffer.writeDecimalLong(1L);
   *
   *   assertEquals("8675309 -123 1", buffer.readUtf8());
   * }</pre>
   */
  BufferedSink writeDecimalLong(long v) throws IOException;

  /**
   * Writes a long to this sink in hexadecimal form (i.e., as a string in base 16). <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeHexadecimalUnsignedLong(65535L);
   *   buffer.writeByte(' ');
   *   buffer.writeHexadecimalUnsignedLong(0xcafebabeL);
   *   buffer.writeByte(' ');
   *   buffer.writeHexadecimalUnsignedLong(0x10L);
   *
   *   assertEquals("ffff cafebabe 10", buffer.readUtf8());
   * }</pre>
   */
  BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException;

  /**
   * Writes all buffered data to the underlying sink, if one exists. Then that sink is recursively
   * flushed which pushes data as far as possible towards its ultimate destination. Typically that
   * destination is a network socket or file. <pre>{@code
   *
   *   BufferedSink b0 = new Buffer();
   *   BufferedSink b1 = Okio.buffer(b0);
   *   BufferedSink b2 = Okio.buffer(b1);
   *
   *   b2.writeUtf8("hello");
   *   assertEquals(5, b2.buffer().size());
   *   assertEquals(0, b1.buffer().size());
   *   assertEquals(0, b0.buffer().size());
   *
   *   b2.flush();
   *   assertEquals(0, b2.buffer().size());
   *   assertEquals(0, b1.buffer().size());
   *   assertEquals(5, b0.buffer().size());
   * }</pre>
   */
  @Override void flush() throws IOException;

  /**
   * Writes all buffered data to the underlying sink, if one exists. Like {@link #flush}, but
   * weaker. Call this before this buffered sink goes out of scope so that its data can reach its
   * destination. <pre>{@code
   *
   *   BufferedSink b0 = new Buffer();
   *   BufferedSink b1 = Okio.buffer(b0);
   *   BufferedSink b2 = Okio.buffer(b1);
   *
   *   b2.writeUtf8("hello");
   *   assertEquals(5, b2.buffer().size());
   *   assertEquals(0, b1.buffer().size());
   *   assertEquals(0, b0.buffer().size());
   *
   *   b2.emit();
   *   assertEquals(0, b2.buffer().size());
   *   assertEquals(5, b1.buffer().size());
   *   assertEquals(0, b0.buffer().size());
   *
   *   b1.emit();
   *   assertEquals(0, b2.buffer().size());
   *   assertEquals(0, b1.buffer().size());
   *   assertEquals(5, b0.buffer().size());
   * }</pre>
   */
  BufferedSink emit() throws IOException;

  /**
   * Writes complete segments to the underlying sink, if one exists. Like {@link #flush}, but
   * weaker. Use this to limit the memory held in the buffer to a single segment. Typically
   * application code will not need to call this: it is only necessary when application code writes
   * directly to this {@linkplain #buffer() sink's buffer}. <pre>{@code
   *
   *   BufferedSink b0 = new Buffer();
   *   BufferedSink b1 = Okio.buffer(b0);
   *   BufferedSink b2 = Okio.buffer(b1);
   *
   *   b2.buffer().write(new byte[20_000]);
   *   assertEquals(20_000, b2.buffer().size());
   *   assertEquals(     0, b1.buffer().size());
   *   assertEquals(     0, b0.buffer().size());
   *
   *   b2.emitCompleteSegments();
   *   assertEquals( 3_616, b2.buffer().size());
   *   assertEquals(     0, b1.buffer().size());
   *   assertEquals(16_384, b0.buffer().size()); // This example assumes 8192 byte segments.
   * }</pre>
   */
  BufferedSink emitCompleteSegments() throws IOException;

  /** Returns an output stream that writes to this sink. */
  OutputStream outputStream();
}
