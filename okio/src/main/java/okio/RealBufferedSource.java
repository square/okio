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
import java.nio.charset.Charset;
import javax.annotation.Nullable;

import static okio.Util.checkOffsetAndCount;

final class RealBufferedSource implements BufferedSource {
  public final Buffer buffer = new Buffer();
  public final Source source;
  boolean closed;

  RealBufferedSource(Source source) {
    if (source == null) throw new NullPointerException("source == null");
    this.source = source;
  }

  @Override public Buffer buffer() {
    return buffer;
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    if (sink == null) throw new IllegalArgumentException("sink == null");
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    if (closed) throw new IllegalStateException("closed");

    if (buffer.size == 0) {
      long read = source.read(buffer, Segment.SIZE);
      if (read == -1) return -1;
    }

    long toRead = Math.min(byteCount, buffer.size);
    return buffer.read(sink, toRead);
  }

  @Override public boolean exhausted() throws IOException {
    if (closed) throw new IllegalStateException("closed");
    return buffer.exhausted() && source.read(buffer, Segment.SIZE) == -1;
  }

  @Override public void require(long byteCount) throws IOException {
    if (!request(byteCount)) throw new EOFException();
  }

  @Override public boolean request(long byteCount) throws IOException {
    if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
    if (closed) throw new IllegalStateException("closed");
    while (buffer.size < byteCount) {
      if (source.read(buffer, Segment.SIZE) == -1) return false;
    }
    return true;
  }

  @Override public byte readByte() throws IOException {
    require(1);
    return buffer.readByte();
  }

  @Override public ByteString readByteString() throws IOException {
    buffer.writeAll(source);
    return buffer.readByteString();
  }

  @Override public ByteString readByteString(long byteCount) throws IOException {
    require(byteCount);
    return buffer.readByteString(byteCount);
  }

  @Override public int select(Options options) throws IOException {
    if (closed) throw new IllegalStateException("closed");

    while (true) {
      int index = buffer.selectPrefix(options);
      if (index == -1) return -1;

      // If the prefix match actually matched a full byte string, consume it and return it.
      int selectedSize = options.byteStrings[index].size();
      if (selectedSize <= buffer.size) {
        buffer.skip(selectedSize);
        return index;
      }

      // We need to grow the buffer. Do that, then try it all again.
      if (source.read(buffer, Segment.SIZE) == -1) return -1;
    }
  }

  @Override public byte[] readByteArray() throws IOException {
    buffer.writeAll(source);
    return buffer.readByteArray();
  }

  @Override public byte[] readByteArray(long byteCount) throws IOException {
    require(byteCount);
    return buffer.readByteArray(byteCount);
  }

  @Override public int read(byte[] sink) throws IOException {
    return read(sink, 0, sink.length);
  }

  @Override public void readFully(byte[] sink) throws IOException {
    try {
      require(sink.length);
    } catch (EOFException e) {
      // The underlying source is exhausted. Copy the bytes we got before rethrowing.
      int offset = 0;
      while (buffer.size > 0) {
        int read = buffer.read(sink, offset, (int) buffer.size);
        if (read == -1) throw new AssertionError();
        offset += read;
      }
      throw e;
    }
    buffer.readFully(sink);
  }

  @Override public int read(byte[] sink, int offset, int byteCount) throws IOException {
    checkOffsetAndCount(sink.length, offset, byteCount);

    if (buffer.size == 0) {
      long read = source.read(buffer, Segment.SIZE);
      if (read == -1) return -1;
    }

    int toRead = (int) Math.min(byteCount, buffer.size);
    return buffer.read(sink, offset, toRead);
  }

  @Override public void readFully(Buffer sink, long byteCount) throws IOException {
    try {
      require(byteCount);
    } catch (EOFException e) {
      // The underlying source is exhausted. Copy the bytes we got before rethrowing.
      sink.writeAll(buffer);
      throw e;
    }
    buffer.readFully(sink, byteCount);
  }

  @Override public long readAll(Sink sink) throws IOException {
    if (sink == null) throw new IllegalArgumentException("sink == null");

    long totalBytesWritten = 0;
    while (source.read(buffer, Segment.SIZE) != -1) {
      long emitByteCount = buffer.completeSegmentByteCount();
      if (emitByteCount > 0) {
        totalBytesWritten += emitByteCount;
        sink.write(buffer, emitByteCount);
      }
    }
    if (buffer.size() > 0) {
      totalBytesWritten += buffer.size();
      sink.write(buffer, buffer.size());
    }
    return totalBytesWritten;
  }

  @Override public String readUtf8() throws IOException {
    buffer.writeAll(source);
    return buffer.readUtf8();
  }

  @Override public String readUtf8(long byteCount) throws IOException {
    require(byteCount);
    return buffer.readUtf8(byteCount);
  }

  @Override public String readString(Charset charset) throws IOException {
    if (charset == null) throw new IllegalArgumentException("charset == null");

    buffer.writeAll(source);
    return buffer.readString(charset);
  }

  @Override public String readString(long byteCount, Charset charset) throws IOException {
    require(byteCount);
    if (charset == null) throw new IllegalArgumentException("charset == null");
    return buffer.readString(byteCount, charset);
  }

  @Override public @Nullable String readUtf8Line() throws IOException {
    long newline = indexOf((byte) '\n');

    if (newline == -1) {
      return buffer.size != 0 ? readUtf8(buffer.size) : null;
    }

    return buffer.readUtf8Line(newline);
  }

  @Override public String readUtf8LineStrict() throws IOException {
    return readUtf8LineStrict(Long.MAX_VALUE);
  }

  @Override public String readUtf8LineStrict(long limit) throws IOException {
    if (limit < 0) throw new IllegalArgumentException("limit < 0: " + limit);
    long scanLength = limit == Long.MAX_VALUE ? Long.MAX_VALUE : limit + 1;
    long newline = indexOf((byte) '\n', 0, scanLength);
    if (newline != -1) return buffer.readUtf8Line(newline);
    if (scanLength < Long.MAX_VALUE
        && request(scanLength) && buffer.getByte(scanLength - 1) == '\r'
        && request(scanLength + 1) && buffer.getByte(scanLength) == '\n') {
      return buffer.readUtf8Line(scanLength); // The line was 'limit' UTF-8 bytes followed by \r\n.
    }
    Buffer data = new Buffer();
    buffer.copyTo(data, 0, Math.min(32, buffer.size()));
    throw new EOFException("\\n not found: limit=" + Math.min(buffer.size(), limit)
        + " content=" + data.readByteString().hex() + '…');
  }

  @Override public int readUtf8CodePoint() throws IOException {
    require(1);

    byte b0 = buffer.getByte(0);
    if ((b0 & 0xe0) == 0xc0) {
      require(2);
    } else if ((b0 & 0xf0) == 0xe0) {
      require(3);
    } else if ((b0 & 0xf8) == 0xf0) {
      require(4);
    }

    return buffer.readUtf8CodePoint();
  }

  @Override public short readShort() throws IOException {
    require(2);
    return buffer.readShort();
  }

  @Override public short readShortLe() throws IOException {
    require(2);
    return buffer.readShortLe();
  }

  @Override public int readInt() throws IOException {
    require(4);
    return buffer.readInt();
  }

  @Override public int readIntLe() throws IOException {
    require(4);
    return buffer.readIntLe();
  }

  @Override public long readLong() throws IOException {
    require(8);
    return buffer.readLong();
  }

  @Override public long readLongLe() throws IOException {
    require(8);
    return buffer.readLongLe();
  }

  @Override public long readDecimalLong() throws IOException {
    require(1);

    for (int pos = 0; request(pos + 1); pos++) {
      byte b = buffer.getByte(pos);
      if ((b < '0' || b > '9') && (pos != 0 || b != '-')) {
        // Non-digit, or non-leading negative sign.
        if (pos == 0) {
          throw new NumberFormatException(String.format(
              "Expected leading [0-9] or '-' character but was %#x", b));
        }
        break;
      }
    }

    return buffer.readDecimalLong();
  }

  @Override public long readHexadecimalUnsignedLong() throws IOException {
    require(1);

    for (int pos = 0; request(pos + 1); pos++) {
      byte b = buffer.getByte(pos);
      if ((b < '0' || b > '9') && (b < 'a' || b > 'f') && (b < 'A' || b > 'F')) {
        // Non-digit, or non-leading negative sign.
        if (pos == 0) {
          throw new NumberFormatException(String.format(
              "Expected leading [0-9a-fA-F] character but was %#x", b));
        }
        break;
      }
    }

    return buffer.readHexadecimalUnsignedLong();
  }

  @Override public void skip(long byteCount) throws IOException {
    if (closed) throw new IllegalStateException("closed");
    while (byteCount > 0) {
      if (buffer.size == 0 && source.read(buffer, Segment.SIZE) == -1) {
        throw new EOFException();
      }
      long toSkip = Math.min(byteCount, buffer.size());
      buffer.skip(toSkip);
      byteCount -= toSkip;
    }
  }

  @Override public long indexOf(byte b) throws IOException {
    return indexOf(b, 0, Long.MAX_VALUE);
  }

  @Override public long indexOf(byte b, long fromIndex) throws IOException {
    return indexOf(b, fromIndex, Long.MAX_VALUE);
  }

  @Override public long indexOf(byte b, long fromIndex, long toIndex) throws IOException {
    if (closed) throw new IllegalStateException("closed");
    if (fromIndex < 0 || toIndex < fromIndex) {
      throw new IllegalArgumentException(
          String.format("fromIndex=%s toIndex=%s", fromIndex, toIndex));
    }

    while (fromIndex < toIndex) {
      long result = buffer.indexOf(b, fromIndex, toIndex);
      if (result != -1L) return result;

      // The byte wasn't in the buffer. Give up if we've already reached our target size or if the
      // underlying stream is exhausted.
      long lastBufferSize = buffer.size;
      if (lastBufferSize >= toIndex || source.read(buffer, Segment.SIZE) == -1) return -1L;

      // Continue the search from where we left off.
      fromIndex = Math.max(fromIndex, lastBufferSize);
    }
    return -1L;
  }

  @Override public long indexOf(ByteString bytes) throws IOException {
    return indexOf(bytes, 0);
  }

  @Override public long indexOf(ByteString bytes, long fromIndex) throws IOException {
    if (closed) throw new IllegalStateException("closed");

    while (true) {
      long result = buffer.indexOf(bytes, fromIndex);
      if (result != -1) return result;

      long lastBufferSize = buffer.size;
      if (source.read(buffer, Segment.SIZE) == -1) return -1L;

      // Keep searching, picking up from where we left off.
      fromIndex = Math.max(fromIndex, lastBufferSize - bytes.size() + 1);
    }
  }

  @Override public long indexOfElement(ByteString targetBytes) throws IOException {
    return indexOfElement(targetBytes, 0);
  }

  @Override public long indexOfElement(ByteString targetBytes, long fromIndex) throws IOException {
    if (closed) throw new IllegalStateException("closed");

    while (true) {
      long result = buffer.indexOfElement(targetBytes, fromIndex);
      if (result != -1) return result;

      long lastBufferSize = buffer.size;
      if (source.read(buffer, Segment.SIZE) == -1) return -1L;

      // Keep searching, picking up from where we left off.
      fromIndex = Math.max(fromIndex, lastBufferSize);
    }
  }

  @Override public boolean rangeEquals(long offset, ByteString bytes) throws IOException {
    return rangeEquals(offset, bytes, 0, bytes.size());
  }

  @Override
  public boolean rangeEquals(long offset, ByteString bytes, int bytesOffset, int byteCount)
      throws IOException {
    if (closed) throw new IllegalStateException("closed");

    if (offset < 0
        || bytesOffset < 0
        || byteCount < 0
        || bytes.size() - bytesOffset < byteCount) {
      return false;
    }
    for (int i = 0; i < byteCount; i++) {
      long bufferOffset = offset + i;
      if (!request(bufferOffset + 1)) return false;
      if (buffer.getByte(bufferOffset) != bytes.getByte(bytesOffset + i)) return false;
    }
    return true;
  }

  @Override public InputStream inputStream() {
    return new InputStream() {
      @Override public int read() throws IOException {
        if (closed) throw new IOException("closed");
        if (buffer.size == 0) {
          long count = source.read(buffer, Segment.SIZE);
          if (count == -1) return -1;
        }
        return buffer.readByte() & 0xff;
      }

      @Override public int read(byte[] data, int offset, int byteCount) throws IOException {
        if (closed) throw new IOException("closed");
        checkOffsetAndCount(data.length, offset, byteCount);

        if (buffer.size == 0) {
          long count = source.read(buffer, Segment.SIZE);
          if (count == -1) return -1;
        }

        return buffer.read(data, offset, byteCount);
      }

      @Override public int available() throws IOException {
        if (closed) throw new IOException("closed");
        return (int) Math.min(buffer.size, Integer.MAX_VALUE);
      }

      @Override public void close() throws IOException {
        RealBufferedSource.this.close();
      }

      @Override public String toString() {
        return RealBufferedSource.this + ".inputStream()";
      }
    };
  }

  @Override public void close() throws IOException {
    if (closed) return;
    closed = true;
    source.close();
    buffer.clear();
  }

  @Override public Timeout timeout() {
    return source.timeout();
  }

  @Override public String toString() {
    return "buffer(" + source + ")";
  }
}
