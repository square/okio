/*
 * Copyright (C) 2019 Square, Inc.
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
package okio

/**
 * A source that keeps a buffer internally so that callers can do small reads without a performance
 * penalty. It also allows clients to read ahead, buffering as much as necessary before consuming
 * input.
 */
expect interface BufferedSource : Source {
  /** This source's internal buffer. */
  val buffer: Buffer

  /**
   * Returns true if there are no more bytes in this source. This will block until there are bytes
   * to read or the source is definitely exhausted.
   */
  fun exhausted(): Boolean

  /**
   * Returns when the buffer contains at least `byteCount` bytes. Throws an
   * [java.io.EOFException] if the source is exhausted before the required bytes can be read.
   */
  fun require(byteCount: Long)

  /**
   * Returns true when the buffer contains at least `byteCount` bytes, expanding it as
   * necessary. Returns false if the source is exhausted before the requested bytes can be read.
   */
  fun request(byteCount: Long): Boolean

  /** Removes a byte from this source and returns it. */
  fun readByte(): Byte

  /**
   * Removes two bytes from this source and returns a big-endian short.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeByte(0x7f)
   *     .writeByte(0xff)
   *     .writeByte(0x00)
   *     .writeByte(0x0f);
   * assertEquals(4, buffer.size());
   *
   * assertEquals(32767, buffer.readShort());
   * assertEquals(2, buffer.size());
   *
   * assertEquals(15, buffer.readShort());
   * assertEquals(0, buffer.size());
   * ```
   */
  fun readShort(): Short

  /**
   * Removes two bytes from this source and returns a little-endian short.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeByte(0xff)
   *     .writeByte(0x7f)
   *     .writeByte(0x0f)
   *     .writeByte(0x00);
   * assertEquals(4, buffer.size());
   *
   * assertEquals(32767, buffer.readShortLe());
   * assertEquals(2, buffer.size());
   *
   * assertEquals(15, buffer.readShortLe());
   * assertEquals(0, buffer.size());
   * ```
   */
  fun readShortLe(): Short

  /**
   * Removes four bytes from this source and returns a big-endian int.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeByte(0x7f)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x0f);
   * assertEquals(8, buffer.size());
   *
   * assertEquals(2147483647, buffer.readInt());
   * assertEquals(4, buffer.size());
   *
   * assertEquals(15, buffer.readInt());
   * assertEquals(0, buffer.size());
   * ```
   */
  fun readInt(): Int

  /**
   * Removes four bytes from this source and returns a little-endian int.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0x7f)
   *     .writeByte(0x0f)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00);
   * assertEquals(8, buffer.size());
   *
   * assertEquals(2147483647, buffer.readIntLe());
   * assertEquals(4, buffer.size());
   *
   * assertEquals(15, buffer.readIntLe());
   * assertEquals(0, buffer.size());
   * ```
   */
  fun readIntLe(): Int

  /**
   * Removes eight bytes from this source and returns a big-endian long.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeByte(0x7f)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x0f);
   * assertEquals(16, buffer.size());
   *
   * assertEquals(9223372036854775807L, buffer.readLong());
   * assertEquals(8, buffer.size());
   *
   * assertEquals(15, buffer.readLong());
   * assertEquals(0, buffer.size());
   * ```
   */
  fun readLong(): Long

  /**
   * Removes eight bytes from this source and returns a little-endian long.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0xff)
   *     .writeByte(0x7f)
   *     .writeByte(0x0f)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00)
   *     .writeByte(0x00);
   * assertEquals(16, buffer.size());
   *
   * assertEquals(9223372036854775807L, buffer.readLongLe());
   * assertEquals(8, buffer.size());
   *
   * assertEquals(15, buffer.readLongLe());
   * assertEquals(0, buffer.size());
   * ```
   */
  fun readLongLe(): Long

  /**
   * Reads a long from this source in signed decimal form (i.e., as a string in base 10 with
   * optional leading '-'). This will iterate until a non-digit character is found.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeUtf8("8675309 -123 00001");
   *
   * assertEquals(8675309L, buffer.readDecimalLong());
   * assertEquals(' ', buffer.readByte());
   * assertEquals(-123L, buffer.readDecimalLong());
   * assertEquals(' ', buffer.readByte());
   * assertEquals(1L, buffer.readDecimalLong());
   * ```
   *
   * @throws NumberFormatException if the found digits do not fit into a `long` or a decimal
   * number was not present.
   */
  fun readDecimalLong(): Long

  /**
   * Reads a long form this source in hexadecimal form (i.e., as a string in base 16). This will
   * iterate until a non-hexadecimal character is found.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeUtf8("ffff CAFEBABE 10");
   *
   * assertEquals(65535L, buffer.readHexadecimalUnsignedLong());
   * assertEquals(' ', buffer.readByte());
   * assertEquals(0xcafebabeL, buffer.readHexadecimalUnsignedLong());
   * assertEquals(' ', buffer.readByte());
   * assertEquals(0x10L, buffer.readHexadecimalUnsignedLong());
   * ```
   *
   * @throws NumberFormatException if the found hexadecimal does not fit into a `long` or
   * hexadecimal was not found.
   */
  fun readHexadecimalUnsignedLong(): Long

  /**
   * Reads and discards `byteCount` bytes from this source. Throws an [java.io.EOFException] if the
   * source is exhausted before the requested bytes can be skipped.
   */
  fun skip(byteCount: Long)

  /** Removes all bytes bytes from this and returns them as a byte string. */
  fun readByteString(): ByteString

  /** Removes `byteCount` bytes from this and returns them as a byte string. */
  fun readByteString(byteCount: Long): ByteString

  /**
   * Finds the first string in `options` that is a prefix of this buffer, consumes it from this
   * buffer, and returns its index. If no byte string in `options` is a prefix of this buffer this
   * returns -1 and no bytes are consumed.
   *
   * This can be used as an alternative to [readByteString] or even [readUtf8] if the set of
   * expected values is known in advance.
   * ```
   * Options FIELDS = Options.of(
   *     ByteString.encodeUtf8("depth="),
   *     ByteString.encodeUtf8("height="),
   *     ByteString.encodeUtf8("width="));
   *
   * Buffer buffer = new Buffer()
   *     .writeUtf8("width=640\n")
   *     .writeUtf8("height=480\n");
   *
   * assertEquals(2, buffer.select(FIELDS));
   * assertEquals(640, buffer.readDecimalLong());
   * assertEquals('\n', buffer.readByte());
   * assertEquals(1, buffer.select(FIELDS));
   * assertEquals(480, buffer.readDecimalLong());
   * assertEquals('\n', buffer.readByte());
   * ```
   */
  fun select(options: Options): Int

  /** Removes all bytes from this and returns them as a byte array. */
  fun readByteArray(): ByteArray

  /** Removes `byteCount` bytes from this and returns them as a byte array. */
  fun readByteArray(byteCount: Long): ByteArray

  /**
   * Removes up to `sink.length` bytes from this and copies them into `sink`. Returns the number of
   * bytes read, or -1 if this source is exhausted.
   */
  fun read(sink: ByteArray): Int

  /**
   * Removes exactly `sink.length` bytes from this and copies them into `sink`. Throws an
   * [java.io.EOFException] if the requested number of bytes cannot be read.
   */
  fun readFully(sink: ByteArray)

  /**
   * Removes up to `byteCount` bytes from this and copies them into `sink` at `offset`. Returns the
   * number of bytes read, or -1 if this source is exhausted.
   */
  fun read(sink: ByteArray, offset: Int, byteCount: Int): Int

  /**
   * Removes exactly `byteCount` bytes from this and appends them to `sink`. Throws an
   * [java.io.EOFException] if the requested number of bytes cannot be read.
   */
  fun readFully(sink: Buffer, byteCount: Long)

  /**
   * Removes all bytes from this and appends them to `sink`. Returns the total number of bytes
   * written to `sink` which will be 0 if this is exhausted.
   */
  fun readAll(sink: Sink): Long

  /**
   * Removes all bytes from this, decodes them as UTF-8, and returns the string. Returns the empty
   * string if this source is empty.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeUtf8("Uh uh uh!")
   *     .writeByte(' ')
   *     .writeUtf8("You didn't say the magic word!");
   *
   * assertEquals("Uh uh uh! You didn't say the magic word!", buffer.readUtf8());
   * assertEquals(0, buffer.size());
   *
   * assertEquals("", buffer.readUtf8());
   * assertEquals(0, buffer.size());
   * ```
   */
  fun readUtf8(): String

  /**
   * Removes `byteCount` bytes from this, decodes them as UTF-8, and returns the string.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeUtf8("Uh uh uh!")
   *     .writeByte(' ')
   *     .writeUtf8("You didn't say the magic word!");
   * assertEquals(40, buffer.size());
   *
   * assertEquals("Uh uh uh! You ", buffer.readUtf8(14));
   * assertEquals(26, buffer.size());
   *
   * assertEquals("didn't say the", buffer.readUtf8(14));
   * assertEquals(12, buffer.size());
   *
   * assertEquals(" magic word!", buffer.readUtf8(12));
   * assertEquals(0, buffer.size());
   * ```
   */
  fun readUtf8(byteCount: Long): String

  /**
   * Removes and returns characters up to but not including the next line break. A line break is
   * either `"\n"` or `"\r\n"`; these characters are not included in the result.
   * ```
   * Buffer buffer = new Buffer()
   *     .writeUtf8("I'm a hacker!\n")
   *     .writeUtf8("That's what I said: you're a nerd.\n")
   *     .writeUtf8("I prefer to be called a hacker!\n");
   * assertEquals(81, buffer.size());
   *
   * assertEquals("I'm a hacker!", buffer.readUtf8Line());
   * assertEquals(67, buffer.size());
   *
   * assertEquals("That's what I said: you're a nerd.", buffer.readUtf8Line());
   * assertEquals(32, buffer.size());
   *
   * assertEquals("I prefer to be called a hacker!", buffer.readUtf8Line());
   * assertEquals(0, buffer.size());
   *
   * assertEquals(null, buffer.readUtf8Line());
   * assertEquals(0, buffer.size());
   * ```
   *
   * **On the end of the stream this method returns null,** just like [java.io.BufferedReader]. If
   * the source doesn't end with a line break then an implicit line break is assumed. Null is
   * returned once the source is exhausted. Use this for human-generated data, where a trailing
   * line break is optional.
   */
  fun readUtf8Line(): String?

  /**
   * Removes and returns characters up to but not including the next line break. A line break is
   * either `"\n"` or `"\r\n"`; these characters are not included in the result.
   *
   * **On the end of the stream this method throws.** Every call must consume either
   * '\r\n' or '\n'. If these characters are absent in the stream, an [java.io.EOFException]
   * is thrown. Use this for machine-generated data where a missing line break implies truncated
   * input.
   */
  fun readUtf8LineStrict(): String

  /**
   * Like [readUtf8LineStrict], except this allows the caller to specify the longest allowed match.
   * Use this to protect against streams that may not include `"\n"` or `"\r\n"`.
   *
   * The returned string will have at most `limit` UTF-8 bytes, and the maximum number of bytes
   * scanned is `limit + 2`. If `limit == 0` this will always throw an `EOFException` because no
   * bytes will be scanned.
   *
   * This method is safe. No bytes are discarded if the match fails, and the caller is free to try
   * another match:
   * ```
   * Buffer buffer = new Buffer();
   * buffer.writeUtf8("12345\r\n");
   *
   * // This will throw! There must be \r\n or \n at the limit or before it.
   * buffer.readUtf8LineStrict(4);
   *
   * // No bytes have been consumed so the caller can retry.
   * assertEquals("12345", buffer.readUtf8LineStrict(5));
   * ```
   */
  fun readUtf8LineStrict(limit: Long): String

  /**
   * Removes and returns a single UTF-8 code point, reading between 1 and 4 bytes as necessary.
   *
   * If this source is exhausted before a complete code point can be read, this throws an
   * [java.io.EOFException] and consumes no input.
   *
   * If this source doesn't start with a properly-encoded UTF-8 code point, this method will remove
   * 1 or more non-UTF-8 bytes and return the replacement character (`U+FFFD`). This covers encoding
   * problems (the input is not properly-encoded UTF-8), characters out of range (beyond the
   * 0x10ffff limit of Unicode), code points for UTF-16 surrogates (U+d800..U+dfff) and overlong
   * encodings (such as `0xc080` for the NUL character in modified UTF-8).
   */
  fun readUtf8CodePoint(): Int

  /** Equivalent to [indexOf(b, 0)][indexOf]. */
  fun indexOf(b: Byte): Long

  /**
   * Returns the index of the first `b` in the buffer at or after `fromIndex`. This expands the
   * buffer as necessary until `b` is found. This reads an unbounded number of bytes into the
   * buffer. Returns -1 if the stream is exhausted before the requested byte is found.
   * ```
   * Buffer buffer = new Buffer();
   * buffer.writeUtf8("Don't move! He can't see us if we don't move.");
   *
   * byte m = 'm';
   * assertEquals(6,  buffer.indexOf(m));
   * assertEquals(40, buffer.indexOf(m, 12));
   * ```
   */
  fun indexOf(b: Byte, fromIndex: Long): Long

  /**
   * Returns the index of `b` if it is found in the range of `fromIndex` inclusive to `toIndex`
   * exclusive. If `b` isn't found, or if `fromIndex == toIndex`, then -1 is returned.
   *
   * The scan terminates at either `toIndex` or the end of the buffer, whichever comes first. The
   * maximum number of bytes scanned is `toIndex-fromIndex`.
   */
  fun indexOf(b: Byte, fromIndex: Long, toIndex: Long): Long

  /** Equivalent to [indexOf(bytes, 0)][indexOf]. */
  fun indexOf(bytes: ByteString): Long

  /**
   * Returns the index of the first match for `bytes` in the buffer at or after `fromIndex`. This
   * expands the buffer as necessary until `bytes` is found. This reads an unbounded number of
   * bytes into the buffer. Returns -1 if the stream is exhausted before the requested bytes are
   * found.
   * ```
   * ByteString MOVE = ByteString.encodeUtf8("move");
   *
   * Buffer buffer = new Buffer();
   * buffer.writeUtf8("Don't move! He can't see us if we don't move.");
   *
   * assertEquals(6,  buffer.indexOf(MOVE));
   * assertEquals(40, buffer.indexOf(MOVE, 12));
   * ```
   */
  fun indexOf(bytes: ByteString, fromIndex: Long): Long

  /** Equivalent to [indexOfElement(targetBytes, 0)][indexOfElement]. */
  fun indexOfElement(targetBytes: ByteString): Long

  /**
   * Returns the first index in this buffer that is at or after `fromIndex` and that contains any of
   * the bytes in `targetBytes`. This expands the buffer as necessary until a target byte is found.
   * This reads an unbounded number of bytes into the buffer. Returns -1 if the stream is exhausted
   * before the requested byte is found.
   * ```
   * ByteString ANY_VOWEL = ByteString.encodeUtf8("AEOIUaeoiu");
   *
   * Buffer buffer = new Buffer();
   * buffer.writeUtf8("Dr. Alan Grant");
   *
   * assertEquals(4,  buffer.indexOfElement(ANY_VOWEL));    // 'A' in 'Alan'.
   * assertEquals(11, buffer.indexOfElement(ANY_VOWEL, 9)); // 'a' in 'Grant'.
   * ```
   */
  fun indexOfElement(targetBytes: ByteString, fromIndex: Long): Long

  /**
   * Returns true if the bytes at `offset` in this source equal `bytes`. This expands the buffer as
   * necessary until a byte does not match, all bytes are matched, or if the stream is exhausted
   * before enough bytes could determine a match.
   * ```
   * ByteString simonSays = ByteString.encodeUtf8("Simon says:");
   *
   * Buffer standOnOneLeg = new Buffer().writeUtf8("Simon says: Stand on one leg.");
   * assertTrue(standOnOneLeg.rangeEquals(0, simonSays));
   *
   * Buffer payMeMoney = new Buffer().writeUtf8("Pay me $1,000,000.");
   * assertFalse(payMeMoney.rangeEquals(0, simonSays));
   * ```
   */
  fun rangeEquals(offset: Long, bytes: ByteString): Boolean

  /**
   * Returns true if `byteCount` bytes at `offset` in this source equal `bytes` at `bytesOffset`.
   * This expands the buffer as necessary until a byte does not match, all bytes are matched, or if
   * the stream is exhausted before enough bytes could determine a match.
   */
  fun rangeEquals(offset: Long, bytes: ByteString, bytesOffset: Int, byteCount: Int): Boolean

  /**
   * Returns a new `BufferedSource` that can read data from this `BufferedSource` without consuming
   * it. The returned source becomes invalid once this source is next read or closed.
   *
   * For example, we can use `peek()` to lookahead and read the same data multiple times.
   *
   * ```
   * val buffer = Buffer()
   * buffer.writeUtf8("abcdefghi")
   *
   * buffer.readUtf8(3) // returns "abc", buffer contains "defghi"
   *
   * val peek = buffer.peek()
   * peek.readUtf8(3) // returns "def", buffer contains "defghi"
   * peek.readUtf8(3) // returns "ghi", buffer contains "defghi"
   *
   * buffer.readUtf8(3) // returns "def", buffer contains "ghi"
   * ```
   */
  fun peek(): BufferedSource
}
