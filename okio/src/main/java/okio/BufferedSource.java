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
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * A source that keeps a buffer internally so that callers can do small reads without a performance
 * penalty. It also allows clients to read ahead, buffering as much as necessary before consuming
 * input.
 */
public interface BufferedSource extends Source {
  /** Returns this source's internal buffer. */
  Buffer buffer();

  /**
   * Returns true if there are no more bytes in this source. This will block until there are bytes
   * to read or the source is definitely exhausted.
   */
  boolean exhausted() throws IOException;

  /**
   * Returns when the buffer contains at least {@code byteCount} bytes. Throws an
   * {@link java.io.EOFException} if the source is exhausted before the required bytes can be read.
   */
  void require(long byteCount) throws IOException;

  /**
   * Returns true when the buffer contains at least {@code byteCount} bytes, expanding it as
   * necessary. Returns false if the source is exhausted before the requested bytes can be read.
   */
  boolean request(long byteCount) throws IOException;

  /** Removes a byte from this source and returns it. */
  byte readByte() throws IOException;

  /**
   * Removes two bytes from this source and returns a big-endian short. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeByte(0x7f)
   *       .writeByte(0xff)
   *       .writeByte(0x00)
   *       .writeByte(0x0f);
   *   assertEquals(4, buffer.size());
   *
   *   assertEquals(32767, buffer.readShort());
   *   assertEquals(2, buffer.size());
   *
   *   assertEquals(15, buffer.readShort());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  short readShort() throws IOException;

  /**
   * Removes two bytes from this source and returns a little-endian short. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeByte(0xff)
   *       .writeByte(0x7f)
   *       .writeByte(0x0f)
   *       .writeByte(0x00);
   *   assertEquals(4, buffer.size());
   *
   *   assertEquals(32767, buffer.readShortLe());
   *   assertEquals(2, buffer.size());
   *
   *   assertEquals(15, buffer.readShortLe());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  short readShortLe() throws IOException;

  /**
   * Removes four bytes from this source and returns a big-endian int. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeByte(0x7f)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x0f);
   *   assertEquals(8, buffer.size());
   *
   *   assertEquals(2147483647, buffer.readInt());
   *   assertEquals(4, buffer.size());
   *
   *   assertEquals(15, buffer.readInt());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  int readInt() throws IOException;

  /**
   * Removes four bytes from this source and returns a little-endian int. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0x7f)
   *       .writeByte(0x0f)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00);
   *   assertEquals(8, buffer.size());
   *
   *   assertEquals(2147483647, buffer.readIntLe());
   *   assertEquals(4, buffer.size());
   *
   *   assertEquals(15, buffer.readIntLe());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  int readIntLe() throws IOException;

  /**
   * Removes eight bytes from this source and returns a big-endian long. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeByte(0x7f)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x0f);
   *   assertEquals(16, buffer.size());
   *
   *   assertEquals(9223372036854775807L, buffer.readLong());
   *   assertEquals(8, buffer.size());
   *
   *   assertEquals(15, buffer.readLong());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  long readLong() throws IOException;

  /**
   * Removes eight bytes from this source and returns a little-endian long. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0xff)
   *       .writeByte(0x7f)
   *       .writeByte(0x0f)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00)
   *       .writeByte(0x00);
   *   assertEquals(16, buffer.size());
   *
   *   assertEquals(9223372036854775807L, buffer.readLongLe());
   *   assertEquals(8, buffer.size());
   *
   *   assertEquals(15, buffer.readLongLe());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  long readLongLe() throws IOException;

  /**
   * Reads a long from this source in signed decimal form (i.e., as a string in base 10 with
   * optional leading '-'). This will iterate until a non-digit character is found. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeUtf8("8675309 -123 00001");
   *
   *   assertEquals(8675309L, buffer.readDecimalLong());
   *   assertEquals(' ', buffer.readByte());
   *   assertEquals(-123L, buffer.readDecimalLong());
   *   assertEquals(' ', buffer.readByte());
   *   assertEquals(1L, buffer.readDecimalLong());
   * }</pre>
   *
   * @throws NumberFormatException if the found digits do not fit into a {@code long} or a decimal
   * number was not present.
   */
  long readDecimalLong() throws IOException;

  /**
   * Reads a long form this source in hexadecimal form (i.e., as a string in base 16). This will
   * iterate until a non-hexadecimal character is found. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeUtf8("ffff CAFEBABE 10");
   *
   *   assertEquals(65535L, buffer.readHexadecimalUnsignedLong());
   *   assertEquals(' ', buffer.readByte());
   *   assertEquals(0xcafebabeL, buffer.readHexadecimalUnsignedLong());
   *   assertEquals(' ', buffer.readByte());
   *   assertEquals(0x10L, buffer.readHexadecimalUnsignedLong());
   * }</pre>
   *
   * @throws NumberFormatException if the found hexadecimal does not fit into a {@code long} or
   * hexadecimal was not found.
   */
  long readHexadecimalUnsignedLong() throws IOException;

  /**
   * Reads and discards {@code byteCount} bytes from this source. Throws an
   * {@link java.io.EOFException} if the source is exhausted before the
   * requested bytes can be skipped.
   */
  void skip(long byteCount) throws IOException;

  /** Removes all bytes bytes from this and returns them as a byte string. */
  ByteString readByteString() throws IOException;

  /** Removes {@code byteCount} bytes from this and returns them as a byte string. */
  ByteString readByteString(long byteCount) throws IOException;

  /**
   * Finds the first string in {@code options} that is a prefix of this buffer, consumes it from
   * this buffer, and returns its index. If no byte string in {@code options} is a prefix of this
   * buffer this returns -1 and no bytes are consumed.
   *
   * <p>This can be used as an alternative to {@link #readByteString} or even {@link #readUtf8} if
   * the set of expected values is known in advance. <pre>{@code
   *
   *   Options FIELDS = Options.of(
   *       ByteString.encodeUtf8("depth="),
   *       ByteString.encodeUtf8("height="),
   *       ByteString.encodeUtf8("width="));
   *
   *   Buffer buffer = new Buffer()
   *       .writeUtf8("width=640\n")
   *       .writeUtf8("height=480\n");
   *
   *   assertEquals(2, buffer.select(FIELDS));
   *   assertEquals(640, buffer.readDecimalLong());
   *   assertEquals('\n', buffer.readByte());
   *   assertEquals(1, buffer.select(FIELDS));
   *   assertEquals(480, buffer.readDecimalLong());
   *   assertEquals('\n', buffer.readByte());
   * }</pre>
   */
  int select(Options options) throws IOException;

  /** Removes all bytes from this and returns them as a byte array. */
  byte[] readByteArray() throws IOException;

  /** Removes {@code byteCount} bytes from this and returns them as a byte array. */
  byte[] readByteArray(long byteCount) throws IOException;

  /**
   * Removes up to {@code sink.length} bytes from this and copies them into {@code sink}. Returns
   * the number of bytes read, or -1 if this source is exhausted.
   */
  int read(byte[] sink) throws IOException;

  /**
   * Removes exactly {@code sink.length} bytes from this and copies them into {@code sink}. Throws
   * an {@link java.io.EOFException} if the requested number of bytes cannot be read.
   */
  void readFully(byte[] sink) throws IOException;

  /**
   * Removes up to {@code byteCount} bytes from this and copies them into {@code sink} at {@code
   * offset}. Returns the number of bytes read, or -1 if this source is exhausted.
   */
  int read(byte[] sink, int offset, int byteCount) throws IOException;

  /**
   * Removes exactly {@code byteCount} bytes from this and appends them to {@code sink}. Throws an
   * {@link java.io.EOFException} if the requested number of bytes cannot be read.
   */
  void readFully(Buffer sink, long byteCount) throws IOException;

  /**
   * Removes all bytes from this and appends them to {@code sink}. Returns the total number of bytes
   * written to {@code sink} which will be 0 if this is exhausted.
   */
  long readAll(Sink sink) throws IOException;

  /**
   * Removes all bytes from this, decodes them as UTF-8, and returns the string. Returns the empty
   * string if this source is empty. <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeUtf8("Uh uh uh!")
   *       .writeByte(' ')
   *       .writeUtf8("You didn't say the magic word!");
   *
   *   assertEquals("Uh uh uh! You didn't say the magic word!", buffer.readUtf8());
   *   assertEquals(0, buffer.size());
   *
   *   assertEquals("", buffer.readUtf8());
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  String readUtf8() throws IOException;

  /**
   * Removes {@code byteCount} bytes from this, decodes them as UTF-8, and returns the string.
   * <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeUtf8("Uh uh uh!")
   *       .writeByte(' ')
   *       .writeUtf8("You didn't say the magic word!");
   *   assertEquals(40, buffer.size());
   *
   *   assertEquals("Uh uh uh! You ", buffer.readUtf8(14));
   *   assertEquals(26, buffer.size());
   *
   *   assertEquals("didn't say the", buffer.readUtf8(14));
   *   assertEquals(12, buffer.size());
   *
   *   assertEquals(" magic word!", buffer.readUtf8(12));
   *   assertEquals(0, buffer.size());
   * }</pre>
   */
  String readUtf8(long byteCount) throws IOException;

  /**
   * Removes and returns characters up to but not including the next line break. A line break is
   * either {@code "\n"} or {@code "\r\n"}; these characters are not included in the result.
   * <pre>{@code
   *
   *   Buffer buffer = new Buffer()
   *       .writeUtf8("I'm a hacker!\n")
   *       .writeUtf8("That's what I said: you're a nerd.\n")
   *       .writeUtf8("I prefer to be called a hacker!\n");
   *   assertEquals(81, buffer.size());
   *
   *   assertEquals("I'm a hacker!", buffer.readUtf8Line());
   *   assertEquals(67, buffer.size());
   *
   *   assertEquals("That's what I said: you're a nerd.", buffer.readUtf8Line());
   *   assertEquals(32, buffer.size());
   *
   *   assertEquals("I prefer to be called a hacker!", buffer.readUtf8Line());
   *   assertEquals(0, buffer.size());
   *
   *   assertEquals(null, buffer.readUtf8Line());
   *   assertEquals(0, buffer.size());
   * }</pre>
   *
   * <p><strong>On the end of the stream this method returns null,</strong> just like {@link
   * java.io.BufferedReader}. If the source doesn't end with a line break then an implicit line
   * break is assumed. Null is returned once the source is exhausted. Use this for human-generated
   * data, where a trailing line break is optional.
   */
  String readUtf8Line() throws IOException;

  /**
   * Removes and returns characters up to but not including the next line break. A line break is
   * either {@code "\n"} or {@code "\r\n"}; these characters are not included in the result.
   *
   * <p><strong>On the end of the stream this method throws.</strong> Every call must consume either
   * '\r\n' or '\n'. If these characters are absent in the stream, an {@link java.io.EOFException}
   * is thrown. Use this for machine-generated data where a missing line break implies truncated
   * input.
   */
  String readUtf8LineStrict() throws IOException;

  /**
   * Like {@link #readUtf8LineStrict()}, but this allows the caller to specify a maximum number of
   * bytes to scan. If {@code limit} bytes are scanned without finding a line break, then an {@link
   * java.io.EOFException} is thrown. A common use case is protecting against input that doesn't
   * include {@code "\n"} or {@code "\r\n"}.
   *
   * <p>This method is safe. No bytes are discarded if the match fails, and the caller is free
   * to try another match: <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeUtf8("12345\r\n");
   *
   *   // This will throw! A newline character (\n) must be read within the limit.
   *   buffer.readUtf8LineStrict(5);
   *
   *   // No bytes have been consumed so the caller can retry.
   *   assertEquals("12345", buffer.readUtf8LineStrict(100));
   * }</pre>
   *
   * <p>The returned string be up to {@code limit - 1} UTF-8 bytes. If {@code limit == 0} this will
   * always throw an {@code EOFException} because no bytes will be scanned.
   */
  String readUtf8LineStrict(long limit) throws IOException;

  /**
   * Removes and returns a single UTF-8 code point, reading between 1 and 4 bytes as necessary.
   *
   * <p>If this source is exhausted before a complete code point can be read, this throws an {@link
   * java.io.EOFException} and consumes no input.
   *
   * <p>If this source doesn't start with a properly-encoded UTF-8 code point, this method will
   * remove 1 or more non-UTF-8 bytes and return the replacement character ({@code U+FFFD}). This
   * covers encoding problems (the input is not properly-encoded UTF-8), characters out of range
   * (beyond the 0x10ffff limit of Unicode), code points for UTF-16 surrogates (U+d800..U+dfff) and
   * overlong encodings (such as {@code 0xc080} for the NUL character in modified UTF-8).
   */
  int readUtf8CodePoint() throws IOException;

  /** Removes all bytes from this, decodes them as {@code charset}, and returns the string. */
  String readString(Charset charset) throws IOException;

  /**
   * Removes {@code byteCount} bytes from this, decodes them as {@code charset}, and returns the
   * string.
   */
  String readString(long byteCount, Charset charset) throws IOException;

  /** Equivalent to {@link #indexOf(byte, long) indexOf(b, 0)}. */
  long indexOf(byte b) throws IOException;

  /**
   * Returns the index of the first {@code b} in the buffer at or after {@code fromIndex}. This
   * expands the buffer as necessary until {@code b} is found. This reads an unbounded number of
   * bytes into the buffer. Returns -1 if the stream is exhausted before the requested byte is
   * found. <pre>{@code
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeUtf8("Don't move! He can't see us if we don't move.");
   *
   *   byte m = 'm';
   *   assertEquals(6,  buffer.indexOf(m));
   *   assertEquals(40, buffer.indexOf(m, 12));
   * }</pre>
   */
  long indexOf(byte b, long fromIndex) throws IOException;

  /**
   * Returns the index of {@code b} if it is found in the range of {@code fromIndex} inclusive
   * to {@code toIndex} exclusive. If {@code b} isn't found, or if {@code fromIndex == toIndex},
   * then -1 is returned.
   *
   * <p>The scan terminates at either {@code toIndex} or the end of the buffer, whichever comes
   * first. The maximum number of bytes scanned is {@code toIndex-fromIndex}.
   */
  long indexOf(byte b, long fromIndex, long toIndex) throws IOException;

  /** Equivalent to {@link #indexOf(ByteString, long) indexOf(bytes, 0)}. */
  long indexOf(ByteString bytes) throws IOException;

  /**
   * Returns the index of the first match for {@code bytes} in the buffer at or after {@code
   * fromIndex}. This expands the buffer as necessary until {@code bytes} is found. This reads an
   * unbounded number of bytes into the buffer. Returns -1 if the stream is exhausted before the
   * requested bytes are found. <pre>{@code
   *
   *   ByteString MOVE = ByteString.encodeUtf8("move");
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeUtf8("Don't move! He can't see us if we don't move.");
   *
   *   assertEquals(6,  buffer.indexOf(MOVE));
   *   assertEquals(40, buffer.indexOf(MOVE, 12));
   * }</pre>
   */
  long indexOf(ByteString bytes, long fromIndex) throws IOException;

  /** Equivalent to {@link #indexOfElement(ByteString, long) indexOfElement(targetBytes, 0)}. */
  long indexOfElement(ByteString targetBytes) throws IOException;

  /**
   * Returns the first index in this buffer that is at or after {@code fromIndex} and that contains
   * any of the bytes in {@code targetBytes}. This expands the buffer as necessary until a target
   * byte is found. This reads an unbounded number of bytes into the buffer. Returns -1 if the
   * stream is exhausted before the requested byte is found. <pre>{@code
   *
   *   ByteString ANY_VOWEL = ByteString.encodeUtf8("AEOIUaeoiu");
   *
   *   Buffer buffer = new Buffer();
   *   buffer.writeUtf8("Dr. Alan Grant");
   *
   *   assertEquals(4,  buffer.indexOfElement(ANY_VOWEL));    // 'A' in 'Alan'.
   *   assertEquals(11, buffer.indexOfElement(ANY_VOWEL, 9)); // 'a' in 'Grant'.
   * }</pre>
   */
  long indexOfElement(ByteString targetBytes, long fromIndex) throws IOException;

  /**
   * Returns true if the bytes at {@code offset} in this source equal {@code bytes}. This expands
   * the buffer as necessary until a byte does not match, all bytes are matched, or if the stream
   * is exhausted before enough bytes could determine a match.  <pre>{@code
   *
   *   ByteString simonSays = ByteString.encodeUtf8("Simon says:");
   *
   *   Buffer standOnOneLeg = new Buffer().writeUtf8("Simon says: Stand on one leg.");
   *   assertTrue(standOnOneLeg.rangeEquals(0, simonSays));
   *
   *   Buffer payMeMoney = new Buffer().writeUtf8("Pay me $1,000,000.");
   *   assertFalse(payMeMoney.rangeEquals(0, simonSays));
   * }</pre>
   */
  boolean rangeEquals(long offset, ByteString bytes) throws IOException;

  /**
   * Returns true if {@code byteCount} bytes at {@code offset} in this source equal {@code bytes}
   * at {@code bytesOffset}. This expands the buffer as necessary until a byte does not match, all
   * bytes are matched, or if the stream is exhausted before enough bytes could determine a match.
   */
  boolean rangeEquals(long offset, ByteString bytes, int bytesOffset, int byteCount)
      throws IOException;

  /** Returns an input stream that reads from this source. */
  InputStream inputStream();
}
