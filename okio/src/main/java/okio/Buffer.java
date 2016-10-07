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
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static okio.Util.checkOffsetAndCount;
import static okio.Util.reverseBytesLong;

/**
 * A collection of bytes in memory.
 *
 * <p><strong>Moving data from one buffer to another is fast.</strong> Instead
 * of copying bytes from one place in memory to another, this class just changes
 * ownership of the underlying byte arrays.
 *
 * <p><strong>This buffer grows with your data.</strong> Just like ArrayList,
 * each buffer starts small. It consumes only the memory it needs to.
 *
 * <p><strong>This buffer pools its byte arrays.</strong> When you allocate a
 * byte array in Java, the runtime must zero-fill the requested array before
 * returning it to you. Even if you're going to write over that space anyway.
 * This class avoids zero-fill and GC churn by pooling byte arrays.
 */
public final class Buffer implements BufferedSource, BufferedSink, Cloneable {
  private static final byte[] DIGITS =
      { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
  static final int REPLACEMENT_CHARACTER = '\ufffd';

  Segment head;
  long size;

  public Buffer() {
  }

  /** Returns the number of bytes currently in this buffer. */
  public long size() {
    return size;
  }

  @Override public Buffer buffer() {
    return this;
  }

  @Override public OutputStream outputStream() {
    return new OutputStream() {
      @Override public void write(int b) {
        writeByte((byte) b);
      }

      @Override public void write(byte[] data, int offset, int byteCount) {
        Buffer.this.write(data, offset, byteCount);
      }

      @Override public void flush() {
      }

      @Override public void close() {
      }

      @Override public String toString() {
        return Buffer.this + ".outputStream()";
      }
    };
  }

  @Override public Buffer emitCompleteSegments() {
    return this; // Nowhere to emit to!
  }

  @Override public BufferedSink emit() {
    return this; // Nowhere to emit to!
  }

  @Override public boolean exhausted() {
    return size == 0;
  }

  @Override public void require(long byteCount) throws EOFException {
    if (size < byteCount) throw new EOFException();
  }

  @Override public boolean request(long byteCount) {
    return size >= byteCount;
  }

  @Override public InputStream inputStream() {
    return new InputStream() {
      @Override public int read() {
        if (size > 0) return readByte() & 0xff;
        return -1;
      }

      @Override public int read(byte[] sink, int offset, int byteCount) {
        return Buffer.this.read(sink, offset, byteCount);
      }

      @Override public int available() {
        return (int) Math.min(size, Integer.MAX_VALUE);
      }

      @Override public void close() {
      }

      @Override public String toString() {
        return Buffer.this + ".inputStream()";
      }
    };
  }

  /** Copy the contents of this to {@code out}. */
  public Buffer copyTo(OutputStream out) throws IOException {
    return copyTo(out, 0, size);
  }

  /**
   * Copy {@code byteCount} bytes from this, starting at {@code offset}, to
   * {@code out}.
   */
  public Buffer copyTo(OutputStream out, long offset, long byteCount) throws IOException {
    if (out == null) throw new IllegalArgumentException("out == null");
    checkOffsetAndCount(size, offset, byteCount);
    if (byteCount == 0) return this;

    // Skip segments that we aren't copying from.
    Segment s = head;
    for (; offset >= (s.limit - s.pos); s = s.next) {
      offset -= (s.limit - s.pos);
    }

    // Copy from one segment at a time.
    for (; byteCount > 0; s = s.next) {
      int pos = (int) (s.pos + offset);
      int toCopy = (int) Math.min(s.limit - pos, byteCount);
      out.write(s.data, pos, toCopy);
      byteCount -= toCopy;
      offset = 0;
    }

    return this;
  }

  /** Copy {@code byteCount} bytes from this, starting at {@code offset}, to {@code out}. */
  public Buffer copyTo(Buffer out, long offset, long byteCount) {
    if (out == null) throw new IllegalArgumentException("out == null");
    checkOffsetAndCount(size, offset, byteCount);
    if (byteCount == 0) return this;

    out.size += byteCount;

    // Skip segments that we aren't copying from.
    Segment s = head;
    for (; offset >= (s.limit - s.pos); s = s.next) {
      offset -= (s.limit - s.pos);
    }

    // Copy one segment at a time.
    for (; byteCount > 0; s = s.next) {
      Segment copy = new Segment(s);
      copy.pos += offset;
      copy.limit = Math.min(copy.pos + (int) byteCount, copy.limit);
      if (out.head == null) {
        out.head = copy.next = copy.prev = copy;
      } else {
        out.head.prev.push(copy);
      }
      byteCount -= copy.limit - copy.pos;
      offset = 0;
    }

    return this;
  }

  /** Write the contents of this to {@code out}. */
  public Buffer writeTo(OutputStream out) throws IOException {
    return writeTo(out, size);
  }

  /** Write {@code byteCount} bytes from this to {@code out}. */
  public Buffer writeTo(OutputStream out, long byteCount) throws IOException {
    if (out == null) throw new IllegalArgumentException("out == null");
    checkOffsetAndCount(size, 0, byteCount);

    Segment s = head;
    while (byteCount > 0) {
      int toCopy = (int) Math.min(byteCount, s.limit - s.pos);
      out.write(s.data, s.pos, toCopy);

      s.pos += toCopy;
      size -= toCopy;
      byteCount -= toCopy;

      if (s.pos == s.limit) {
        Segment toRecycle = s;
        head = s = toRecycle.pop();
        SegmentPool.recycle(toRecycle);
      }
    }

    return this;
  }

  /** Read and exhaust bytes from {@code in} to this. */
  public Buffer readFrom(InputStream in) throws IOException {
    readFrom(in, Long.MAX_VALUE, true);
    return this;
  }

  /** Read {@code byteCount} bytes from {@code in} to this. */
  public Buffer readFrom(InputStream in, long byteCount) throws IOException {
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    readFrom(in, byteCount, false);
    return this;
  }

  private void readFrom(InputStream in, long byteCount, boolean forever) throws IOException {
    if (in == null) throw new IllegalArgumentException("in == null");
    while (byteCount > 0 || forever) {
      Segment tail = writableSegment(1);
      int maxToCopy = (int) Math.min(byteCount, Segment.SIZE - tail.limit);
      int bytesRead = in.read(tail.data, tail.limit, maxToCopy);
      if (bytesRead == -1) {
        if (forever) return;
        throw new EOFException();
      }
      tail.limit += bytesRead;
      size += bytesRead;
      byteCount -= bytesRead;
    }
  }

  /**
   * Returns the number of bytes in segments that are not writable. This is the
   * number of bytes that can be flushed immediately to an underlying sink
   * without harming throughput.
   */
  public long completeSegmentByteCount() {
    long result = size;
    if (result == 0) return 0;

    // Omit the tail if it's still writable.
    Segment tail = head.prev;
    if (tail.limit < Segment.SIZE && tail.owner) {
      result -= tail.limit - tail.pos;
    }

    return result;
  }

  @Override public byte readByte() {
    if (size == 0) throw new IllegalStateException("size == 0");

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    byte[] data = segment.data;
    byte b = data[pos++];
    size -= 1;

    if (pos == limit) {
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return b;
  }

  /** Returns the byte at {@code pos}. */
  public byte getByte(long pos) {
    checkOffsetAndCount(size, pos, 1);
    for (Segment s = head; true; s = s.next) {
      int segmentByteCount = s.limit - s.pos;
      if (pos < segmentByteCount) return s.data[s.pos + (int) pos];
      pos -= segmentByteCount;
    }
  }

  @Override public short readShort() {
    if (size < 2) throw new IllegalStateException("size < 2: " + size);

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    // If the short is split across multiple segments, delegate to readByte().
    if (limit - pos < 2) {
      int s = (readByte() & 0xff) << 8
          |   (readByte() & 0xff);
      return (short) s;
    }

    byte[] data = segment.data;
    int s = (data[pos++] & 0xff) << 8
        |   (data[pos++] & 0xff);
    size -= 2;

    if (pos == limit) {
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return (short) s;
  }

  @Override public int readInt() {
    if (size < 4) throw new IllegalStateException("size < 4: " + size);

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    // If the int is split across multiple segments, delegate to readByte().
    if (limit - pos < 4) {
      return (readByte() & 0xff) << 24
          |  (readByte() & 0xff) << 16
          |  (readByte() & 0xff) <<  8
          |  (readByte() & 0xff);
    }

    byte[] data = segment.data;
    int i = (data[pos++] & 0xff) << 24
        |   (data[pos++] & 0xff) << 16
        |   (data[pos++] & 0xff) <<  8
        |   (data[pos++] & 0xff);
    size -= 4;

    if (pos == limit) {
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return i;
  }

  @Override public long readLong() {
    if (size < 8) throw new IllegalStateException("size < 8: " + size);

    Segment segment = head;
    int pos = segment.pos;
    int limit = segment.limit;

    // If the long is split across multiple segments, delegate to readInt().
    if (limit - pos < 8) {
      return (readInt() & 0xffffffffL) << 32
          |  (readInt() & 0xffffffffL);
    }

    byte[] data = segment.data;
    long v = (data[pos++] & 0xffL) << 56
        |    (data[pos++] & 0xffL) << 48
        |    (data[pos++] & 0xffL) << 40
        |    (data[pos++] & 0xffL) << 32
        |    (data[pos++] & 0xffL) << 24
        |    (data[pos++] & 0xffL) << 16
        |    (data[pos++] & 0xffL) <<  8
        |    (data[pos++] & 0xffL);
    size -= 8;

    if (pos == limit) {
      head = segment.pop();
      SegmentPool.recycle(segment);
    } else {
      segment.pos = pos;
    }

    return v;
  }

  @Override public short readShortLe() {
    return Util.reverseBytesShort(readShort());
  }

  @Override public int readIntLe() {
    return Util.reverseBytesInt(readInt());
  }

  @Override public long readLongLe() {
    return Util.reverseBytesLong(readLong());
  }

  @Override public long readDecimalLong() {
    if (size == 0) throw new IllegalStateException("size == 0");

    // This value is always built negatively in order to accommodate Long.MIN_VALUE.
    long value = 0;
    int seen = 0;
    boolean negative = false;
    boolean done = false;

    long overflowZone = Long.MIN_VALUE / 10;
    long overflowDigit = (Long.MIN_VALUE % 10) + 1;

    do {
      Segment segment = head;

      byte[] data = segment.data;
      int pos = segment.pos;
      int limit = segment.limit;

      for (; pos < limit; pos++, seen++) {
        byte b = data[pos];
        if (b >= '0' && b <= '9') {
          int digit = '0' - b;

          // Detect when the digit would cause an overflow.
          if (value < overflowZone || value == overflowZone && digit < overflowDigit) {
            Buffer buffer = new Buffer().writeDecimalLong(value).writeByte(b);
            if (!negative) buffer.readByte(); // Skip negative sign.
            throw new NumberFormatException("Number too large: " + buffer.readUtf8());
          }
          value *= 10;
          value += digit;
        } else if (b == '-' && seen == 0) {
          negative = true;
          overflowDigit -= 1;
        } else {
          if (seen == 0) {
            throw new NumberFormatException(
                "Expected leading [0-9] or '-' character but was 0x" + Integer.toHexString(b));
          }
          // Set a flag to stop iteration. We still need to run through segment updating below.
          done = true;
          break;
        }
      }

      if (pos == limit) {
        head = segment.pop();
        SegmentPool.recycle(segment);
      } else {
        segment.pos = pos;
      }
    } while (!done && head != null);

    size -= seen;
    return negative ? value : -value;
  }

  @Override public long readHexadecimalUnsignedLong() {
    if (size == 0) throw new IllegalStateException("size == 0");

    long value = 0;
    int seen = 0;
    boolean done = false;

    do {
      Segment segment = head;

      byte[] data = segment.data;
      int pos = segment.pos;
      int limit = segment.limit;

      for (; pos < limit; pos++, seen++) {
        int digit;

        byte b = data[pos];
        if (b >= '0' && b <= '9') {
          digit = b - '0';
        } else if (b >= 'a' && b <= 'f') {
          digit = b - 'a' + 10;
        } else if (b >= 'A' && b <= 'F') {
          digit = b - 'A' + 10; // We never write uppercase, but we support reading it.
        } else {
          if (seen == 0) {
            throw new NumberFormatException(
                "Expected leading [0-9a-fA-F] character but was 0x" + Integer.toHexString(b));
          }
          // Set a flag to stop iteration. We still need to run through segment updating below.
          done = true;
          break;
        }

        // Detect when the shift will overflow.
        if ((value & 0xf000000000000000L) != 0) {
          Buffer buffer = new Buffer().writeHexadecimalUnsignedLong(value).writeByte(b);
          throw new NumberFormatException("Number too large: " + buffer.readUtf8());
        }

        value <<= 4;
        value |= digit;
      }

      if (pos == limit) {
        head = segment.pop();
        SegmentPool.recycle(segment);
      } else {
        segment.pos = pos;
      }
    } while (!done && head != null);

    size -= seen;
    return value;
  }

  @Override public ByteString readByteString() {
    return new ByteString(readByteArray());
  }

  @Override public ByteString readByteString(long byteCount) throws EOFException {
    return new ByteString(readByteArray(byteCount));
  }

  @Override public int select(Options options) {
    Segment s = head;
    if (s == null) return options.indexOf(ByteString.EMPTY);

    ByteString[] byteStrings = options.byteStrings;
    for (int i = 0, listSize = byteStrings.length; i < listSize; i++) {
      ByteString b = byteStrings[i];
      if (size >= b.size() && rangeEquals(s, s.pos, b, 0, b.size())) {
        try {
          skip(b.size());
          return i;
        } catch (EOFException e) {
          throw new AssertionError(e);
        }
      }
    }
    return -1;
  }

  /**
   * Returns the index of a value in {@code options} that is either the prefix of this buffer, or
   * that this buffer is a prefix of. Unlike {@link #select} this never consumes the value, even
   * if it is found in full.
   */
  int selectPrefix(Options options) {
    Segment s = head;
    ByteString[] byteStrings = options.byteStrings;
    for (int i = 0, listSize = byteStrings.length; i < listSize; i++) {
      ByteString b = byteStrings[i];
      int bytesLimit = (int) Math.min(size, b.size());
      if (bytesLimit == 0 || rangeEquals(s, s.pos, b, 0, bytesLimit)) {
        return i;
      }
    }
    return -1;
  }

  @Override public void readFully(Buffer sink, long byteCount) throws EOFException {
    if (size < byteCount) {
      sink.write(this, size); // Exhaust ourselves.
      throw new EOFException();
    }
    sink.write(this, byteCount);
  }

  @Override public long readAll(Sink sink) throws IOException {
    long byteCount = size;
    if (byteCount > 0) {
      sink.write(this, byteCount);
    }
    return byteCount;
  }

  @Override public String readUtf8() {
    try {
      return readString(size, Util.UTF_8);
    } catch (EOFException e) {
      throw new AssertionError(e);
    }
  }

  @Override public String readUtf8(long byteCount) throws EOFException {
    return readString(byteCount, Util.UTF_8);
  }

  @Override public String readString(Charset charset) {
    try {
      return readString(size, charset);
    } catch (EOFException e) {
      throw new AssertionError(e);
    }
  }

  @Override public String readString(long byteCount, Charset charset) throws EOFException {
    checkOffsetAndCount(size, 0, byteCount);
    if (charset == null) throw new IllegalArgumentException("charset == null");
    if (byteCount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("byteCount > Integer.MAX_VALUE: " + byteCount);
    }
    if (byteCount == 0) return "";

    Segment s = head;
    if (s.pos + byteCount > s.limit) {
      // If the string spans multiple segments, delegate to readBytes().
      return new String(readByteArray(byteCount), charset);
    }

    String result = new String(s.data, s.pos, (int) byteCount, charset);
    s.pos += byteCount;
    size -= byteCount;

    if (s.pos == s.limit) {
      head = s.pop();
      SegmentPool.recycle(s);
    }

    return result;
  }

  @Override public String readUtf8Line() throws EOFException {
    long newline = indexOf((byte) '\n');

    if (newline == -1) {
      return size != 0 ? readUtf8(size) : null;
    }

    return readUtf8Line(newline);
  }

  @Override public String readUtf8LineStrict() throws EOFException {
    long newline = indexOf((byte) '\n');
    if (newline == -1) {
      Buffer data = new Buffer();
      copyTo(data, 0, Math.min(32, size));
      throw new EOFException("\\n not found: size=" + size()
          + " content=" + data.readByteString().hex() + "…");
    }
    return readUtf8Line(newline);
  }

  String readUtf8Line(long newline) throws EOFException {
    if (newline > 0 && getByte(newline - 1) == '\r') {
      // Read everything until '\r\n', then skip the '\r\n'.
      String result = readUtf8((newline - 1));
      skip(2);
      return result;

    } else {
      // Read everything until '\n', then skip the '\n'.
      String result = readUtf8(newline);
      skip(1);
      return result;
    }
  }

  @Override public int readUtf8CodePoint() throws EOFException {
    if (size == 0) throw new EOFException();

    byte b0 = getByte(0);
    int codePoint;
    int byteCount;
    int min;

    if ((b0 & 0x80) == 0) {
      // 0xxxxxxx.
      codePoint = b0 & 0x7f;
      byteCount = 1; // 7 bits (ASCII).
      min = 0x0;

    } else if ((b0 & 0xe0) == 0xc0) {
      // 0x110xxxxx
      codePoint = b0 & 0x1f;
      byteCount = 2; // 11 bits (5 + 6).
      min = 0x80;

    } else if ((b0 & 0xf0) == 0xe0) {
      // 0x1110xxxx
      codePoint = b0 & 0x0f;
      byteCount = 3; // 16 bits (4 + 6 + 6).
      min = 0x800;

    } else if ((b0 & 0xf8) == 0xf0) {
      // 0x11110xxx
      codePoint = b0 & 0x07;
      byteCount = 4; // 21 bits (3 + 6 + 6 + 6).
      min = 0x10000;

    } else {
      // We expected the first byte of a code point but got something else.
      skip(1);
      return REPLACEMENT_CHARACTER;
    }

    if (size < byteCount) {
      throw new EOFException("size < " + byteCount + ": " + size
          + " (to read code point prefixed 0x" + Integer.toHexString(b0) + ")");
    }

    // Read the continuation bytes. If we encounter a non-continuation byte, the sequence consumed
    // thus far is truncated and is decoded as the replacement character. That non-continuation byte
    // is left in the stream for processing by the next call to readUtf8CodePoint().
    for (int i = 1; i < byteCount; i++) {
      byte b = getByte(i);
      if ((b & 0xc0) == 0x80) {
        // 0x10xxxxxx
        codePoint <<= 6;
        codePoint |= b & 0x3f;
      } else {
        skip(i);
        return REPLACEMENT_CHARACTER;
      }
    }

    skip(byteCount);

    if (codePoint > 0x10ffff) {
      return REPLACEMENT_CHARACTER; // Reject code points larger than the Unicode maximum.
    }

    if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
      return REPLACEMENT_CHARACTER; // Reject partial surrogates.
    }

    if (codePoint < min) {
      return REPLACEMENT_CHARACTER; // Reject overlong code points.
    }

    return codePoint;
  }

  @Override public byte[] readByteArray() {
    try {
      return readByteArray(size);
    } catch (EOFException e) {
      throw new AssertionError(e);
    }
  }

  @Override public byte[] readByteArray(long byteCount) throws EOFException {
    checkOffsetAndCount(size, 0, byteCount);
    if (byteCount > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("byteCount > Integer.MAX_VALUE: " + byteCount);
    }

    byte[] result = new byte[(int) byteCount];
    readFully(result);
    return result;
  }

  @Override public int read(byte[] sink) {
    return read(sink, 0, sink.length);
  }

  @Override public void readFully(byte[] sink) throws EOFException {
    int offset = 0;
    while (offset < sink.length) {
      int read = read(sink, offset, sink.length - offset);
      if (read == -1) throw new EOFException();
      offset += read;
    }
  }

  @Override public int read(byte[] sink, int offset, int byteCount) {
    checkOffsetAndCount(sink.length, offset, byteCount);

    Segment s = head;
    if (s == null) return -1;
    int toCopy = Math.min(byteCount, s.limit - s.pos);
    System.arraycopy(s.data, s.pos, sink, offset, toCopy);

    s.pos += toCopy;
    size -= toCopy;

    if (s.pos == s.limit) {
      head = s.pop();
      SegmentPool.recycle(s);
    }

    return toCopy;
  }

  /**
   * Discards all bytes in this buffer. Calling this method when you're done
   * with a buffer will return its segments to the pool.
   */
  public void clear() {
    try {
      skip(size);
    } catch (EOFException e) {
      throw new AssertionError(e);
    }
  }

  /** Discards {@code byteCount} bytes from the head of this buffer. */
  @Override public void skip(long byteCount) throws EOFException {
    while (byteCount > 0) {
      if (head == null) throw new EOFException();

      int toSkip = (int) Math.min(byteCount, head.limit - head.pos);
      size -= toSkip;
      byteCount -= toSkip;
      head.pos += toSkip;

      if (head.pos == head.limit) {
        Segment toRecycle = head;
        head = toRecycle.pop();
        SegmentPool.recycle(toRecycle);
      }
    }
  }

  @Override public Buffer write(ByteString byteString) {
    if (byteString == null) throw new IllegalArgumentException("byteString == null");
    byteString.write(this);
    return this;
  }

  @Override public Buffer writeUtf8(String string) {
    return writeUtf8(string, 0, string.length());
  }

  @Override public Buffer writeUtf8(String string, int beginIndex, int endIndex) {
    if (string == null) throw new IllegalArgumentException("string == null");
    if (beginIndex < 0) throw new IllegalAccessError("beginIndex < 0: " + beginIndex);
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + beginIndex);
    }
    if (endIndex > string.length()) {
      throw new IllegalArgumentException(
          "endIndex > string.length: " + endIndex + " > " + string.length());
    }

    // Transcode a UTF-16 Java String to UTF-8 bytes.
    for (int i = beginIndex; i < endIndex;) {
      int c = string.charAt(i);

      if (c < 0x80) {
        Segment tail = writableSegment(1);
        byte[] data = tail.data;
        int segmentOffset = tail.limit - i;
        int runLimit = Math.min(endIndex, Segment.SIZE - segmentOffset);

        // Emit a 7-bit character with 1 byte.
        data[segmentOffset + i++] = (byte) c; // 0xxxxxxx

        // Fast-path contiguous runs of ASCII characters. This is ugly, but yields a ~4x performance
        // improvement over independent calls to writeByte().
        while (i < runLimit) {
          c = string.charAt(i);
          if (c >= 0x80) break;
          data[segmentOffset + i++] = (byte) c; // 0xxxxxxx
        }

        int runSize = i + segmentOffset - tail.limit; // Equivalent to i - (previous i).
        tail.limit += runSize;
        size += runSize;

      } else if (c < 0x800) {
        // Emit a 11-bit character with 2 bytes.
        writeByte(c >>  6        | 0xc0); // 110xxxxx
        writeByte(c       & 0x3f | 0x80); // 10xxxxxx
        i++;

      } else if (c < 0xd800 || c > 0xdfff) {
        // Emit a 16-bit character with 3 bytes.
        writeByte(c >> 12        | 0xe0); // 1110xxxx
        writeByte(c >>  6 & 0x3f | 0x80); // 10xxxxxx
        writeByte(c       & 0x3f | 0x80); // 10xxxxxx
        i++;

      } else {
        // c is a surrogate. Make sure it is a high surrogate & that its successor is a low
        // surrogate. If not, the UTF-16 is invalid, in which case we emit a replacement character.
        int low = i + 1 < endIndex ? string.charAt(i + 1) : 0;
        if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
          writeByte('?');
          i++;
          continue;
        }

        // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
        // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
        // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
        int codePoint = 0x010000 + ((c & ~0xd800) << 10 | low & ~0xdc00);

        // Emit a 21-bit character with 4 bytes.
        writeByte(codePoint >> 18        | 0xf0); // 11110xxx
        writeByte(codePoint >> 12 & 0x3f | 0x80); // 10xxxxxx
        writeByte(codePoint >>  6 & 0x3f | 0x80); // 10xxyyyy
        writeByte(codePoint       & 0x3f | 0x80); // 10yyyyyy
        i += 2;
      }
    }

    return this;
  }

  @Override public Buffer writeUtf8CodePoint(int codePoint) {
    if (codePoint < 0x80) {
      // Emit a 7-bit code point with 1 byte.
      writeByte(codePoint);

    } else if (codePoint < 0x800) {
      // Emit a 11-bit code point with 2 bytes.
      writeByte(codePoint >>  6        | 0xc0); // 110xxxxx
      writeByte(codePoint       & 0x3f | 0x80); // 10xxxxxx

    } else if (codePoint < 0x10000) {
      if (codePoint >= 0xd800 && codePoint <= 0xdfff) {
        throw new IllegalArgumentException(
            "Unexpected code point: " + Integer.toHexString(codePoint));
      }

      // Emit a 16-bit code point with 3 bytes.
      writeByte(codePoint >> 12        | 0xe0); // 1110xxxx
      writeByte(codePoint >>  6 & 0x3f | 0x80); // 10xxxxxx
      writeByte(codePoint       & 0x3f | 0x80); // 10xxxxxx

    } else if (codePoint <= 0x10ffff) {
      // Emit a 21-bit code point with 4 bytes.
      writeByte(codePoint >> 18        | 0xf0); // 11110xxx
      writeByte(codePoint >> 12 & 0x3f | 0x80); // 10xxxxxx
      writeByte(codePoint >>  6 & 0x3f | 0x80); // 10xxxxxx
      writeByte(codePoint       & 0x3f | 0x80); // 10xxxxxx

    } else {
      throw new IllegalArgumentException(
          "Unexpected code point: " + Integer.toHexString(codePoint));
    }

    return this;
  }

  @Override public Buffer writeString(String string, Charset charset) {
    return writeString(string, 0, string.length(), charset);
  }

  @Override
  public Buffer writeString(String string, int beginIndex, int endIndex, Charset charset) {
    if (string == null) throw new IllegalArgumentException("string == null");
    if (beginIndex < 0) throw new IllegalAccessError("beginIndex < 0: " + beginIndex);
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + beginIndex);
    }
    if (endIndex > string.length()) {
      throw new IllegalArgumentException(
          "endIndex > string.length: " + endIndex + " > " + string.length());
    }
    if (charset == null) throw new IllegalArgumentException("charset == null");
    if (charset.equals(Util.UTF_8)) return writeUtf8(string, beginIndex, endIndex);
    byte[] data = string.substring(beginIndex, endIndex).getBytes(charset);
    return write(data, 0, data.length);
  }

  @Override public Buffer write(byte[] source) {
    if (source == null) throw new IllegalArgumentException("source == null");
    return write(source, 0, source.length);
  }

  @Override public Buffer write(byte[] source, int offset, int byteCount) {
    if (source == null) throw new IllegalArgumentException("source == null");
    checkOffsetAndCount(source.length, offset, byteCount);

    int limit = offset + byteCount;
    while (offset < limit) {
      Segment tail = writableSegment(1);

      int toCopy = Math.min(limit - offset, Segment.SIZE - tail.limit);
      System.arraycopy(source, offset, tail.data, tail.limit, toCopy);

      offset += toCopy;
      tail.limit += toCopy;
    }

    size += byteCount;
    return this;
  }

  @Override public long writeAll(Source source) throws IOException {
    if (source == null) throw new IllegalArgumentException("source == null");
    long totalBytesRead = 0;
    for (long readCount; (readCount = source.read(this, Segment.SIZE)) != -1; ) {
      totalBytesRead += readCount;
    }
    return totalBytesRead;
  }

  @Override public BufferedSink write(Source source, long byteCount) throws IOException {
    while (byteCount > 0) {
      long read = source.read(this, byteCount);
      if (read == -1) throw new EOFException();
      byteCount -= read;
    }
    return this;
  }

  @Override public Buffer writeByte(int b) {
    Segment tail = writableSegment(1);
    tail.data[tail.limit++] = (byte) b;
    size += 1;
    return this;
  }

  @Override public Buffer writeShort(int s) {
    Segment tail = writableSegment(2);
    byte[] data = tail.data;
    int limit = tail.limit;
    data[limit++] = (byte) ((s >>> 8) & 0xff);
    data[limit++] = (byte)  (s        & 0xff);
    tail.limit = limit;
    size += 2;
    return this;
  }

  @Override public Buffer writeShortLe(int s) {
    return writeShort(Util.reverseBytesShort((short) s));
  }

  @Override public Buffer writeInt(int i) {
    Segment tail = writableSegment(4);
    byte[] data = tail.data;
    int limit = tail.limit;
    data[limit++] = (byte) ((i >>> 24) & 0xff);
    data[limit++] = (byte) ((i >>> 16) & 0xff);
    data[limit++] = (byte) ((i >>>  8) & 0xff);
    data[limit++] = (byte)  (i         & 0xff);
    tail.limit = limit;
    size += 4;
    return this;
  }

  @Override public Buffer writeIntLe(int i) {
    return writeInt(Util.reverseBytesInt(i));
  }

  @Override public Buffer writeLong(long v) {
    Segment tail = writableSegment(8);
    byte[] data = tail.data;
    int limit = tail.limit;
    data[limit++] = (byte) ((v >>> 56L) & 0xff);
    data[limit++] = (byte) ((v >>> 48L) & 0xff);
    data[limit++] = (byte) ((v >>> 40L) & 0xff);
    data[limit++] = (byte) ((v >>> 32L) & 0xff);
    data[limit++] = (byte) ((v >>> 24L) & 0xff);
    data[limit++] = (byte) ((v >>> 16L) & 0xff);
    data[limit++] = (byte) ((v >>>  8L) & 0xff);
    data[limit++] = (byte)  (v          & 0xff);
    tail.limit = limit;
    size += 8;
    return this;
  }

  @Override public Buffer writeLongLe(long v) {
    return writeLong(reverseBytesLong(v));
  }

  @Override public Buffer writeDecimalLong(long v) {
    if (v == 0) {
      // Both a shortcut and required since the following code can't handle zero.
      return writeByte('0');
    }

    boolean negative = false;
    if (v < 0) {
      v = -v;
      if (v < 0) { // Only true for Long.MIN_VALUE.
        return writeUtf8("-9223372036854775808");
      }
      negative = true;
    }

    // Binary search for character width which favors matching lower numbers.
    int width = //
          v < 100000000L
        ? v < 10000L
        ? v < 100L
        ? v < 10L ? 1 : 2
        : v < 1000L ? 3 : 4
        : v < 1000000L
        ? v < 100000L ? 5 : 6
        : v < 10000000L ? 7 : 8
        : v < 1000000000000L
        ? v < 10000000000L
        ? v < 1000000000L ? 9 : 10
        : v < 100000000000L ? 11 : 12
        : v < 1000000000000000L
        ? v < 10000000000000L ? 13
        : v < 100000000000000L ? 14 : 15
        : v < 100000000000000000L
        ? v < 10000000000000000L ? 16 : 17
        : v < 1000000000000000000L ? 18 : 19;
    if (negative) {
      ++width;
    }

    Segment tail = writableSegment(width);
    byte[] data = tail.data;
    int pos = tail.limit + width; // We write backwards from right to left.
    while (v != 0) {
      int digit = (int) (v % 10);
      data[--pos] = DIGITS[digit];
      v /= 10;
    }
    if (negative) {
      data[--pos] = '-';
    }

    tail.limit += width;
    this.size += width;
    return this;
  }

  @Override public Buffer writeHexadecimalUnsignedLong(long v) {
    if (v == 0) {
      // Both a shortcut and required since the following code can't handle zero.
      return writeByte('0');
    }

    int width = Long.numberOfTrailingZeros(Long.highestOneBit(v)) / 4 + 1;

    Segment tail = writableSegment(width);
    byte[] data = tail.data;
    for (int pos = tail.limit + width - 1, start = tail.limit; pos >= start; pos--) {
      data[pos] = DIGITS[(int) (v & 0xF)];
      v >>>= 4;
    }
    tail.limit += width;
    size += width;
    return this;
  }

  /**
   * Returns a tail segment that we can write at least {@code minimumCapacity}
   * bytes to, creating it if necessary.
   */
  Segment writableSegment(int minimumCapacity) {
    if (minimumCapacity < 1 || minimumCapacity > Segment.SIZE) throw new IllegalArgumentException();

    if (head == null) {
      head = SegmentPool.take(); // Acquire a first segment.
      return head.next = head.prev = head;
    }

    Segment tail = head.prev;
    if (tail.limit + minimumCapacity > Segment.SIZE || !tail.owner) {
      tail = tail.push(SegmentPool.take()); // Append a new empty segment to fill up.
    }
    return tail;
  }

  @Override public void write(Buffer source, long byteCount) {
    // Move bytes from the head of the source buffer to the tail of this buffer
    // while balancing two conflicting goals: don't waste CPU and don't waste
    // memory.
    //
    //
    // Don't waste CPU (ie. don't copy data around).
    //
    // Copying large amounts of data is expensive. Instead, we prefer to
    // reassign entire segments from one buffer to the other.
    //
    //
    // Don't waste memory.
    //
    // As an invariant, adjacent pairs of segments in a buffer should be at
    // least 50% full, except for the head segment and the tail segment.
    //
    // The head segment cannot maintain the invariant because the application is
    // consuming bytes from this segment, decreasing its level.
    //
    // The tail segment cannot maintain the invariant because the application is
    // producing bytes, which may require new nearly-empty tail segments to be
    // appended.
    //
    //
    // Moving segments between buffers
    //
    // When writing one buffer to another, we prefer to reassign entire segments
    // over copying bytes into their most compact form. Suppose we have a buffer
    // with these segment levels [91%, 61%]. If we append a buffer with a
    // single [72%] segment, that yields [91%, 61%, 72%]. No bytes are copied.
    //
    // Or suppose we have a buffer with these segment levels: [100%, 2%], and we
    // want to append it to a buffer with these segment levels [99%, 3%]. This
    // operation will yield the following segments: [100%, 2%, 99%, 3%]. That
    // is, we do not spend time copying bytes around to achieve more efficient
    // memory use like [100%, 100%, 4%].
    //
    // When combining buffers, we will compact adjacent buffers when their
    // combined level doesn't exceed 100%. For example, when we start with
    // [100%, 40%] and append [30%, 80%], the result is [100%, 70%, 80%].
    //
    //
    // Splitting segments
    //
    // Occasionally we write only part of a source buffer to a sink buffer. For
    // example, given a sink [51%, 91%], we may want to write the first 30% of
    // a source [92%, 82%] to it. To simplify, we first transform the source to
    // an equivalent buffer [30%, 62%, 82%] and then move the head segment,
    // yielding sink [51%, 91%, 30%] and source [62%, 82%].

    if (source == null) throw new IllegalArgumentException("source == null");
    if (source == this) throw new IllegalArgumentException("source == this");
    checkOffsetAndCount(source.size, 0, byteCount);

    while (byteCount > 0) {
      // Is a prefix of the source's head segment all that we need to move?
      if (byteCount < (source.head.limit - source.head.pos)) {
        Segment tail = head != null ? head.prev : null;
        if (tail != null && tail.owner
            && (byteCount + tail.limit - (tail.shared ? 0 : tail.pos) <= Segment.SIZE)) {
          // Our existing segments are sufficient. Move bytes from source's head to our tail.
          source.head.writeTo(tail, (int) byteCount);
          source.size -= byteCount;
          size += byteCount;
          return;
        } else {
          // We're going to need another segment. Split the source's head
          // segment in two, then move the first of those two to this buffer.
          source.head = source.head.split((int) byteCount);
        }
      }

      // Remove the source's head segment and append it to our tail.
      Segment segmentToMove = source.head;
      long movedByteCount = segmentToMove.limit - segmentToMove.pos;
      source.head = segmentToMove.pop();
      if (head == null) {
        head = segmentToMove;
        head.next = head.prev = head;
      } else {
        Segment tail = head.prev;
        tail = tail.push(segmentToMove);
        tail.compact();
      }
      source.size -= movedByteCount;
      size += movedByteCount;
      byteCount -= movedByteCount;
    }
  }

  @Override public long read(Buffer sink, long byteCount) {
    if (sink == null) throw new IllegalArgumentException("sink == null");
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    if (size == 0) return -1L;
    if (byteCount > size) byteCount = size;
    sink.write(this, byteCount);
    return byteCount;
  }

  @Override public long indexOf(byte b) {
    return indexOf(b, 0);
  }

  /**
   * Returns the index of {@code b} in this at or beyond {@code fromIndex}, or
   * -1 if this buffer does not contain {@code b} in that range.
   */
  @Override public long indexOf(byte b, long fromIndex) {
    if (fromIndex < 0) throw new IllegalArgumentException("fromIndex < 0");

    Segment s;
    long offset;

    // TODO(jwilson): extract this to a shared helper method when can do so without allocating.
    findSegmentAndOffset: {
      // Pick the first segment to scan. This is the first segment with offset <= fromIndex.
      s = head;
      if (s == null) {
        // No segments to scan!
        return -1L;
      } else if (size - fromIndex < fromIndex) {
        // We're scanning in the back half of this buffer. Find the segment starting at the back.
        offset = size;
        while (offset > fromIndex) {
          s = s.prev;
          offset -= (s.limit - s.pos);
        }
      } else {
        // We're scanning in the front half of this buffer. Find the segment starting at the front.
        offset = 0L;
        for (long nextOffset; (nextOffset = offset + (s.limit - s.pos)) < fromIndex; ) {
          s = s.next;
          offset = nextOffset;
        }
      }
    }

    // Scan through the segments, searching for b.
    while (offset < size) {
      byte[] data = s.data;
      for (int pos = (int) (s.pos + fromIndex - offset), limit = s.limit; pos < limit; pos++) {
        if (data[pos] == b) {
          return pos - s.pos + offset;
        }
      }

      // Not in this segment. Try the next one.
      offset += (s.limit - s.pos);
      fromIndex = offset;
      s = s.next;
    }

    return -1L;
  }

  @Override public long indexOf(ByteString bytes) throws IOException {
    return indexOf(bytes, 0);
  }

  @Override public long indexOf(ByteString bytes, long fromIndex) throws IOException {
    if (bytes.size() == 0) throw new IllegalArgumentException("bytes is empty");
    if (fromIndex < 0) throw new IllegalArgumentException("fromIndex < 0");

    Segment s;
    long offset;

    // TODO(jwilson): extract this to a shared helper method when can do so without allocating.
    findSegmentAndOffset: {
      // Pick the first segment to scan. This is the first segment with offset <= fromIndex.
      s = head;
      if (s == null) {
        // No segments to scan!
        return -1L;
      } else if (size - fromIndex < fromIndex) {
        // We're scanning in the back half of this buffer. Find the segment starting at the back.
        offset = size;
        while (offset > fromIndex) {
          s = s.prev;
          offset -= (s.limit - s.pos);
        }
      } else {
        // We're scanning in the front half of this buffer. Find the segment starting at the front.
        offset = 0L;
        for (long nextOffset; (nextOffset = offset + (s.limit - s.pos)) < fromIndex; ) {
          s = s.next;
          offset = nextOffset;
        }
      }
    }

    // Scan through the segments, searching for the lead byte. Each time that is found, delegate to
    // rangeEquals() to check for a complete match.
    byte b0 = bytes.getByte(0);
    int bytesSize = bytes.size();
    long resultLimit = size - bytesSize + 1;
    while (offset < resultLimit) {
      // Scan through the current segment.
      byte[] data = s.data;
      int segmentLimit = (int) Math.min(s.limit, s.pos + resultLimit - offset);
      for (int pos = (int) (s.pos + fromIndex - offset); pos < segmentLimit; pos++) {
        if (data[pos] == b0 && rangeEquals(s, pos + 1, bytes, 1, bytesSize)) {
          return pos - s.pos + offset;
        }
      }

      // Not in this segment. Try the next one.
      offset += (s.limit - s.pos);
      fromIndex = offset;
      s = s.next;
    }

    return -1L;
  }

  @Override public long indexOfElement(ByteString targetBytes) {
    return indexOfElement(targetBytes, 0);
  }

  @Override public long indexOfElement(ByteString targetBytes, long fromIndex) {
    if (fromIndex < 0) throw new IllegalArgumentException("fromIndex < 0");

    Segment s;
    long offset;

    // TODO(jwilson): extract this to a shared helper method when can do so without allocating.
    findSegmentAndOffset: {
      // Pick the first segment to scan. This is the first segment with offset <= fromIndex.
      s = head;
      if (s == null) {
        // No segments to scan!
        return -1L;
      } else if (size - fromIndex < fromIndex) {
        // We're scanning in the back half of this buffer. Find the segment starting at the back.
        offset = size;
        while (offset > fromIndex) {
          s = s.prev;
          offset -= (s.limit - s.pos);
        }
      } else {
        // We're scanning in the front half of this buffer. Find the segment starting at the front.
        offset = 0L;
        for (long nextOffset; (nextOffset = offset + (s.limit - s.pos)) < fromIndex; ) {
          s = s.next;
          offset = nextOffset;
        }
      }
    }

    // Special case searching for one of two bytes. This is a common case for tools like Moshi,
    // which search for pairs of chars like `\r` and `\n` or {@code `"` and `\`. The impact of this
    // optimization is a ~5x speedup for this case without a substantial cost to other cases.
    if (targetBytes.size() == 2) {
      // Scan through the segments, searching for either of the two bytes.
      byte b0 = targetBytes.getByte(0);
      byte b1 = targetBytes.getByte(1);
      while (offset < size) {
        byte[] data = s.data;
        for (int pos = (int) (s.pos + fromIndex - offset), limit = s.limit; pos < limit; pos++) {
          int b = data[pos];
          if (b == b0 || b == b1) {
            return pos - s.pos + offset;
          }
        }

        // Not in this segment. Try the next one.
        offset += (s.limit - s.pos);
        fromIndex = offset;
        s = s.next;
      }
    } else {
      // Scan through the segments, searching for a byte that's also in the array.
      byte[] targetByteArray = targetBytes.internalArray();
      while (offset < size) {
        byte[] data = s.data;
        for (int pos = (int) (s.pos + fromIndex - offset), limit = s.limit; pos < limit; pos++) {
          int b = data[pos];
          for (byte t : targetByteArray) {
            if (b == t) return pos - s.pos + offset;
          }
        }

        // Not in this segment. Try the next one.
        offset += (s.limit - s.pos);
        fromIndex = offset;
        s = s.next;
      }
    }

    return -1L;
  }

  @Override public boolean rangeEquals(long offset, ByteString bytes) {
    return rangeEquals(offset, bytes, 0, bytes.size());
  }

  @Override public boolean rangeEquals(long offset, ByteString bytes, int bytesOffset, int byteCount) {
    if (offset < 0
        || bytesOffset < 0
        || byteCount < 0
        || size - offset < byteCount
        || bytes.size() - bytesOffset < byteCount) {
      return false;
    }
    for (int i = 0; i < byteCount; i++) {
      if (getByte(offset + i) != bytes.getByte(bytesOffset + i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true if the range within this buffer starting at {@code segmentPos} in {@code segment}
   * is equal to {@code bytes[bytesOffset..bytesLimit)}.
   */
  private boolean rangeEquals(
      Segment segment, int segmentPos, ByteString bytes, int bytesOffset, int bytesLimit) {
    int segmentLimit = segment.limit;
    byte[] data = segment.data;

    for (int i = bytesOffset; i < bytesLimit; ) {
      if (segmentPos == segmentLimit) {
        segment = segment.next;
        data = segment.data;
        segmentPos = segment.pos;
        segmentLimit = segment.limit;
      }

      if (data[segmentPos] != bytes.getByte(i)) {
        return false;
      }

      segmentPos++;
      i++;
    }

    return true;
  }

  @Override public void flush() {
  }

  @Override public void close() {
  }

  @Override public Timeout timeout() {
    return Timeout.NONE;
  }

  /** For testing. This returns the sizes of the segments in this buffer. */
  List<Integer> segmentSizes() {
    if (head == null) return Collections.emptyList();
    List<Integer> result = new ArrayList<>();
    result.add(head.limit - head.pos);
    for (Segment s = head.next; s != head; s = s.next) {
      result.add(s.limit - s.pos);
    }
    return result;
  }

  /** Returns the 128-bit MD5 hash of this buffer. */
  public ByteString md5() {
    return digest("MD5");
  }

  /** Returns the 160-bit SHA-1 hash of this buffer. */
  public ByteString sha1() {
    return digest("SHA-1");
  }

  /** Returns the 256-bit SHA-256 hash of this buffer. */
  public ByteString sha256() {
    return digest("SHA-256");
  }

  private ByteString digest(String algorithm) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
      if (head != null) {
        messageDigest.update(head.data, head.pos, head.limit - head.pos);
        for (Segment s = head.next; s != head; s = s.next) {
          messageDigest.update(s.data, s.pos, s.limit - s.pos);
        }
      }
      return ByteString.of(messageDigest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError();
    }
  }

  /** Returns the 160-bit SHA-1 HMAC of this buffer. */
  public ByteString hmacSha1(ByteString key) {
    return hmac("HmacSHA1", key);
  }

  /** Returns the 256-bit SHA-256 HMAC of this buffer. */
  public ByteString hmacSha256(ByteString key) {
    return hmac("HmacSHA256", key);
  }

  private ByteString hmac(String algorithm, ByteString key) {
    try {
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(key.toByteArray(), algorithm));
      if (head != null) {
        mac.update(head.data, head.pos, head.limit - head.pos);
        for (Segment s = head.next; s != head; s = s.next) {
          mac.update(s.data, s.pos, s.limit - s.pos);
        }
      }
      return ByteString.of(mac.doFinal());
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError();
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Buffer)) return false;
    Buffer that = (Buffer) o;
    if (size != that.size) return false;
    if (size == 0) return true; // Both buffers are empty.

    Segment sa = this.head;
    Segment sb = that.head;
    int posA = sa.pos;
    int posB = sb.pos;

    for (long pos = 0, count; pos < size; pos += count) {
      count = Math.min(sa.limit - posA, sb.limit - posB);

      for (int i = 0; i < count; i++) {
        if (sa.data[posA++] != sb.data[posB++]) return false;
      }

      if (posA == sa.limit) {
        sa = sa.next;
        posA = sa.pos;
      }

      if (posB == sb.limit) {
        sb = sb.next;
        posB = sb.pos;
      }
    }

    return true;
  }

  @Override public int hashCode() {
    Segment s = head;
    if (s == null) return 0;
    int result = 1;
    do {
      for (int pos = s.pos, limit = s.limit; pos < limit; pos++) {
        result = 31 * result + s.data[pos];
      }
      s = s.next;
    } while (s != head);
    return result;
  }

  /**
   * Returns a human-readable string that describes the contents of this buffer. Typically this
   * is a string like {@code [text=Hello]} or {@code [hex=0000ffff]}.
   */
  @Override public String toString() {
    return snapshot().toString();
  }

  /** Returns a deep copy of this buffer. */
  @Override public Buffer clone() {
    Buffer result = new Buffer();
    if (size == 0) return result;

    result.head = new Segment(head);
    result.head.next = result.head.prev = result.head;
    for (Segment s = head.next; s != head; s = s.next) {
      result.head.prev.push(new Segment(s));
    }
    result.size = size;
    return result;
  }

  /** Returns an immutable copy of this buffer as a byte string. */
  public ByteString snapshot() {
    if (size > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("size > Integer.MAX_VALUE: " + size);
    }
    return snapshot((int) size);
  }

  /**
   * Returns an immutable copy of the first {@code byteCount} bytes of this buffer as a byte string.
   */
  public ByteString snapshot(int byteCount) {
    if (byteCount == 0) return ByteString.EMPTY;
    return new SegmentedByteString(this, byteCount);
  }
}
