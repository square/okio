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
 * A source that keeps a buffer internally so that callers can do small reads
 * without a performance penalty. It also allows clients to read ahead,
 * buffering as much as necessary before consuming input.
 */
public interface BufferedSource extends Source {
  /** Returns this source's internal buffer. */
  Buffer buffer();

  /**
   * Returns true if there are no more bytes in this source. This will block
   * until there are bytes to read or the source is definitely exhausted.
   */
  boolean exhausted() throws IOException;

  /**
   * Returns when the buffer contains at least {@code byteCount} bytes. Throws
   * an {@link java.io.EOFException} if the source is exhausted before the
   * required bytes can be read.
   */
  void require(long byteCount) throws IOException;

  /**
   * Returns true when the buffer contains at least {@code byteCount} bytes,
   * expanding it as necessary. Returns false if the source is exhausted before
   * the requested bytes can be read.
   */
  boolean request(long byteCount) throws IOException;

  /** Removes a byte from this source and returns it. */
  byte readByte() throws IOException;

  /** Removes two bytes from this source and returns a big-endian short. */
  short readShort() throws IOException;

  /** Removes two bytes from this source and returns a little-endian short. */
  short readShortLe() throws IOException;

  /** Removes four bytes from this source and returns a big-endian int. */
  int readInt() throws IOException;

  /** Removes four bytes from this source and returns a little-endian int. */
  int readIntLe() throws IOException;

  /** Removes eight bytes from this source and returns a big-endian long. */
  long readLong() throws IOException;

  /** Removes eight bytes from this source and returns a little-endian long. */
  long readLongLe() throws IOException;

  /**
   * Reads a long from this source in signed decimal form (i.e., as a string in base 10 with
   * optional leading '-'). This will iterate until a non-digit character is found.
   *
   * @throws NumberFormatException if the found digits do not fit into a {@code long} or a decimal
   * number was not present.
   */
  long readDecimalLong() throws IOException;

  /**
   * Reads a long form this source in hexadecimal form (i.e., as a string in base 16). This will
   * iterate until a non-hexadecimal character is found.
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

  /** Removes all bytes from this and returns them as a byte array. */
  byte[] readByteArray() throws IOException;

  /** Removes {@code byteCount} bytes from this and returns them as a byte array. */
  byte[] readByteArray(long byteCount) throws IOException;

  /**
   * Removes up to {@code sink.length} bytes from this and copies them into {@code sink}.
   * Returns the number of bytes read, or -1 if this source is exhausted.
   */
  int read(byte[] sink) throws IOException;

  /**
   * Removes exactly {@code sink.length} bytes from this and copies them into {@code sink}.
   * Throws an {@link java.io.EOFException} if the requested number of bytes cannot be read.
   */
  void readFully(byte[] sink) throws IOException;

  /**
   * Removes up to {@code byteCount} bytes from this and copies them into {@code sink} at
   * {@code offset}. Returns the number of bytes read, or -1 if this source is exhausted.
   */
  int read(byte[] sink, int offset, int byteCount) throws IOException;

  /**
   * Removes exactly {@code byteCount} bytes from this and appends them to
   * {@code sink}. Throws an {@link java.io.EOFException} if the requested
   * number of bytes cannot be read.
   */
  void readFully(Buffer sink, long byteCount) throws IOException;

  /**
   * Removes all bytes from this and appends them to {@code sink}. Returns the
   * total number of bytes written to {@code sink} which will be 0 if this is
   * exhausted.
   */
  long readAll(Sink sink) throws IOException;

  /** Removes all bytes from this, decodes them as UTF-8, and returns the string. */
  String readUtf8() throws IOException;

  /**
   * Removes {@code byteCount} bytes from this, decodes them as UTF-8, and
   * returns the string.
   */
  String readUtf8(long byteCount) throws IOException;

  /**
   * Removes and returns characters up to but not including the next line break.
   * A line break is either {@code "\n"} or {@code "\r\n"}; these characters are
   * not included in the result.
   *
   * <p><strong>On the end of the stream this method returns null,</strong> just
   * like {@link java.io.BufferedReader}. If the source doesn't end with a line
   * break then an implicit line break is assumed. Null is returned once the
   * source is exhausted. Use this for human-generated data, where a trailing
   * line break is optional.
   */
  String readUtf8Line() throws IOException;

  /**
   * Removes and returns characters up to but not including the next line break.
   * A line break is either {@code "\n"} or {@code "\r\n"}; these characters are
   * not included in the result.
   *
   * <p><strong>On the end of the stream this method throws.</strong> Every call
   * must consume either '\r\n' or '\n'. If these characters are absent in the
   * stream, an {@link java.io.EOFException} is thrown. Use this for
   * machine-generated data where a missing line break implies truncated input.
   */
  String readUtf8LineStrict() throws IOException;

  /**
   * Removes all bytes from this, decodes them as {@code charset}, and returns
   * the string.
   */
  String readString(Charset charset) throws IOException;

  /**
   * Removes {@code byteCount} bytes from this, decodes them as {@code charset},
   * and returns the string.
   */
  String readString(long byteCount, Charset charset) throws IOException;

  /**
   * Returns the index of the first {@code b} in the buffer. This expands the
   * buffer as necessary until {@code b} is found. This reads an unbounded
   * number of bytes into the buffer. Returns -1 if the stream is exhausted
   * before the requested byte is found.
   */
  long indexOf(byte b) throws IOException;

  /**
   * Returns the index of the first {@code b} in the buffer at or after {@code
   * fromIndex}. This expands the buffer as necessary until {@code b} is found.
   * This reads an unbounded number of bytes into the buffer. Returns -1 if the
   * stream is exhausted before the requested byte is found.
   */
  long indexOf(byte b, long fromIndex) throws IOException;

  /**
   * Returns the index of the first byte in {@code targetBytes} in the buffer.
   * This expands the buffer as necessary until a target byte is found. This
   * reads an unbounded number of bytes into the buffer. Returns -1 if the
   * stream is exhausted before the requested byte is found.
   */
  long indexOfElement(ByteString targetBytes) throws IOException;

  /**
   * Returns the index of the first byte in {@code targetBytes} in the buffer
   * at or after {@code fromIndex}. This expands the buffer as necessary until
   * a target byte is found. This reads an unbounded number of bytes into the
   * buffer. Returns -1 if the stream is exhausted before the requested byte is
   * found.
   */
  long indexOfElement(ByteString targetBytes, long fromIndex) throws IOException;

  /** Returns an input stream that reads from this source. */
  InputStream inputStream();
}
