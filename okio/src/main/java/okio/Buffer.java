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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        return this + ".outputStream()";
      }
    };
  }

  @Override public Buffer emitCompleteSegments() {
    return this; // Nowhere to emit to!
  }

  @Override public BufferedSink emit() throws IOException {
    return this; // Nowhere to emit to!
  }

  @Override public boolean exhausted() {
    return size == 0;
  }

  @Override public void require(long byteCount) throws EOFException {
    if (size < byteCount) throw new EOFException();
  }

  @Override public boolean request(long byteCount) throws IOException {
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

    Segment source = head;
    Segment target = out.writableSegment(1);
    out.size += byteCount;

    while (byteCount > 0) {
      // If necessary, advance to a readable source segment. This won't repeat after the first copy.
      while (offset >= source.limit - source.pos) {
        offset -= (source.limit - source.pos);
        source = source.next;
      }

      // If necessary, append another target segment.
      if (target.limit == Segment.SIZE) {
        target = target.push(SegmentPool.INSTANCE.take());
      }

      // Copy bytes from the source segment to the target segment.
      long sourceReadable = Math.min(source.limit - (source.pos + offset), byteCount);
      long targetWritable = Segment.SIZE - target.limit;
      int toCopy = (int) Math.min(sourceReadable, targetWritable);
      System.arraycopy(source.data, source.pos + (int) offset, target.data, target.limit, toCopy);
      offset += toCopy;
      target.limit += toCopy;
      byteCount -= toCopy;
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
        SegmentPool.INSTANCE.recycle(toRecycle);
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
    if (tail.limit < Segment.SIZE) {
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
      SegmentPool.INSTANCE.recycle(segment);
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
      SegmentPool.INSTANCE.recycle(segment);
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
      SegmentPool.INSTANCE.recycle(segment);
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
      SegmentPool.INSTANCE.recycle(segment);
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

  @Override public ByteString readByteString() {
    return new ByteString(readByteArray());
  }

  @Override public ByteString readByteString(long byteCount) throws EOFException {
    return new ByteString(readByteArray(byteCount));
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
      SegmentPool.INSTANCE.recycle(s);
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
          + " content=" + data.readByteString().hex() + "...");
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
      SegmentPool.INSTANCE.recycle(s);
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
        SegmentPool.INSTANCE.recycle(toRecycle);
      }
    }
  }

  @Override public Buffer write(ByteString byteString) {
    if (byteString == null) throw new IllegalArgumentException("byteString == null");
    return write(byteString.data, 0, byteString.data.length);
  }

  @Override public Buffer writeUtf8(String string) {
    if (string == null) throw new IllegalArgumentException("string == null");

    // Transcode a UTF-16 Java String to UTF-8 bytes.
    for (int i = 0, length = string.length(); i < length;) {
      int c = string.charAt(i);

      if (c < 0x80) {
        Segment tail = writableSegment(1);
        byte[] data = tail.data;
        int segmentOffset = tail.limit - i;
        int runLimit = Math.min(length, Segment.SIZE - segmentOffset);

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
        int low = i + 1 < length ? string.charAt(i + 1) : 0;
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

  @Override public Buffer writeString(String string, Charset charset) {
    if (string == null) throw new IllegalArgumentException("string == null");
    if (charset == null) throw new IllegalArgumentException("charset == null");
    if (charset.equals(Util.UTF_8)) return writeUtf8(string);
    byte[] data = string.getBytes(charset);
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
    if (byteCount > 0) {
      source.read(this, byteCount);
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

  /**
   * Returns a tail segment that we can write at least {@code minimumCapacity}
   * bytes to, creating it if necessary.
   */
  Segment writableSegment(int minimumCapacity) {
    if (minimumCapacity < 1 || minimumCapacity > Segment.SIZE) throw new IllegalArgumentException();

    if (head == null) {
      head = SegmentPool.INSTANCE.take(); // Acquire a first segment.
      return head.next = head.prev = head;
    }

    Segment tail = head.prev;
    if (tail.limit + minimumCapacity > Segment.SIZE) {
      tail = tail.push(SegmentPool.INSTANCE.take()); // Append a new empty segment to fill up.
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
        if (tail == null || byteCount + (tail.limit - tail.pos) > Segment.SIZE) {
          // We're going to need another segment. Split the source's head
          // segment in two, then move the first of those two to this buffer.
          source.head = source.head.split((int) byteCount);
        } else {
          // Our existing segments are sufficient. Move bytes from source's head to our tail.
          source.head.writeTo(tail, (int) byteCount);
          source.size -= byteCount;
          size += byteCount;
          return;
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

    Segment s = head;
    if (s == null) return -1L;
    long offset = 0L;
    do {
      int segmentByteCount = s.limit - s.pos;
      if (fromIndex >= segmentByteCount) {
        fromIndex -= segmentByteCount;
      } else {
        byte[] data = s.data;
        for (long pos = s.pos + fromIndex, limit = s.limit; pos < limit; pos++) {
          if (data[(int) pos] == b) return offset + pos - s.pos;
        }
        fromIndex = 0;
      }
      offset += segmentByteCount;
      s = s.next;
    } while (s != head);
    return -1L;
  }

  @Override public long indexOfElement(ByteString targetBytes) {
    return indexOfElement(targetBytes, 0);
  }

  @Override public long indexOfElement(ByteString targetBytes, long fromIndex) {
    if (fromIndex < 0) throw new IllegalArgumentException("fromIndex < 0");

    Segment s = head;
    if (s == null) return -1L;
    long offset = 0L;
    byte[] toFind = targetBytes.data;
    do {
      int segmentByteCount = s.limit - s.pos;
      if (fromIndex >= segmentByteCount) {
        fromIndex -= segmentByteCount;
      } else {
        byte[] data = s.data;
        for (long pos = s.pos + fromIndex, limit = s.limit; pos < limit; pos++) {
          byte b = data[(int) pos];
          for (byte targetByte : toFind) {
            if (b == targetByte) return offset + pos - s.pos;
          }
        }
        fromIndex = 0;
      }
      offset += segmentByteCount;
      s = s.next;
    } while (s != head);
    return -1L;
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

  @Override public String toString() {
    if (size == 0) {
      return "Buffer[size=0]";
    }

    if (size <= 16) {
      ByteString data = clone().readByteString();
      return String.format("Buffer[size=%s data=%s]", size, data.hex());
    }

    try {
      MessageDigest md5 = MessageDigest.getInstance("MD5");
      md5.update(head.data, head.pos, head.limit - head.pos);
      for (Segment s = head.next; s != head; s = s.next) {
        md5.update(s.data, s.pos, s.limit - s.pos);
      }
      return String.format("Buffer[size=%s md5=%s]",
          size, ByteString.of(md5.digest()).hex());
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError();
    }
  }

  /** Returns a deep copy of this buffer. */
  @Override public Buffer clone() {
    Buffer result = new Buffer();
    if (size == 0) return result;

    result.write(head.data, head.pos, head.limit - head.pos);
    for (Segment s = head.next; s != head; s = s.next) {
      result.write(s.data, s.pos, s.limit - s.pos);
    }

    return result;
  }
}
