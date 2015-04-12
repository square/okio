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

  /** Encodes {@code string} in UTF-8 and writes it to this sink. */
  BufferedSink writeUtf8(String string) throws IOException;

  /**
   * Encodes the characters at {@code beginIndex} up to {@code endIndex} from {@code string} in
   * UTF-8 and writes it to this sink.
   */
  BufferedSink writeUtf8(String string, int beginIndex, int endIndex) throws IOException;

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

  /** Writes a big-endian short to this sink using two bytes. */
  BufferedSink writeShort(int s) throws IOException;

  /** Writes a little-endian short to this sink using two bytes. */
  BufferedSink writeShortLe(int s) throws IOException;

  /** Writes a big-endian int to this sink using four bytes. */
  BufferedSink writeInt(int i) throws IOException;

  /** Writes a little-endian int to this sink using four bytes. */
  BufferedSink writeIntLe(int i) throws IOException;

  /** Writes a big-endian long to this sink using eight bytes. */
  BufferedSink writeLong(long v) throws IOException;

  /** Writes a little-endian long to this sink using eight bytes. */
  BufferedSink writeLongLe(long v) throws IOException;

  /** Writes a long to this sink in signed decimal form (i.e., as a string in base 10). */
  BufferedSink writeDecimalLong(long v) throws IOException;

  /** Writes a long to this sink in hexadecimal form (i.e., as a string in base 16). */
  BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException;

  /**
   * Writes complete segments to the underlying sink, if one exists. Like {@link #flush}, but
   * weaker. Use this to limit the memory held in the buffer to a single segment.
   */
  BufferedSink emitCompleteSegments() throws IOException;

  /**
   * Writes all buffered data to the underlying sink, if one exists. Like {@link #flush}, but
   * weaker. Call this before this buffered sink goes out of scope so that its data can reach its
   * destination.
   */
  BufferedSink emit() throws IOException;

  /** Returns an output stream that writes to this sink. */
  OutputStream outputStream();
}
