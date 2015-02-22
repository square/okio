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

import static okio.Util.checkOffsetAndCount;

final class RealBufferedSource implements BufferedSource {
  public final Buffer buffer;
  public final Source source;
  private boolean closed;

  public RealBufferedSource(Source source, Buffer buffer) {
    if (source == null) throw new IllegalArgumentException("source == null");
    this.buffer = buffer;
    this.source = source;
  }

  public RealBufferedSource(Source source) {
    this(source, new Buffer());
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
        int read = buffer.read(sink, offset, (int) buffer.size - offset);
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

  @Override public String readUtf8Line() throws IOException {
    long newline = indexOf((byte) '\n');

    if (newline == -1) {
      return buffer.size != 0 ? readUtf8(buffer.size) : null;
    }

    return buffer.readUtf8Line(newline);
  }

  @Override public String readUtf8LineStrict() throws IOException {
    long newline = indexOf((byte) '\n');
    if (newline == -1L) {
      Buffer data = new Buffer();
      buffer.copyTo(data, 0, Math.min(32, buffer.size()));
      throw new EOFException("\\n not found: size=" + buffer.size()
          + " content=" + data.readByteString().hex() + "...");
    }
    return buffer.readUtf8Line(newline);
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
    int pos = 0;
    while (true) {
      if (!request(pos + 1)) {
        break; // No more data.
      }
      byte b = buffer.getByte(pos);
      if ((b < '0' || b > '9') && (pos != 0 || b != '-')) {
        break; // Non-digit, or non-leading negative sign.
      }
      pos++;
    }
    if (pos == 0) {
      throw new NumberFormatException("Expected leading [0-9] or '-' character but was 0x"
          + Integer.toHexString(buffer.getByte(0)));
    }

    return buffer.readDecimalLong();
  }

  @Override public long readHexadecimalUnsignedLong() throws IOException {
    int pos = 0;
    while (true) {
      if (!request(pos + 1)) {
        break; // No more data.
      }
      byte b = buffer.getByte(pos);
      if ((b < '0' || b > '9') && (b < 'a' || b > 'f') && (b < 'A' || b > 'F')) {
        break; // Non-digit, or non-leading negative sign.
      }
      pos += 1;
    }
    if (pos == 0) {
      throw new NumberFormatException("Expected leading [0-9a-fA-F] character but was 0x"
          + Integer.toHexString(buffer.getByte(0)));
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
    return indexOf(b, 0);
  }

  @Override public long indexOf(byte b, long fromIndex) throws IOException {
    if (closed) throw new IllegalStateException("closed");
    while (fromIndex >= buffer.size) {
      if (source.read(buffer, Segment.SIZE) == -1) return -1L;
    }
    long index;
    while ((index = buffer.indexOf(b, fromIndex)) == -1) {
      fromIndex = buffer.size;
      if (source.read(buffer, Segment.SIZE) == -1) return -1L;
    }
    return index;
  }

  @Override public long indexOfElement(ByteString targetBytes) throws IOException {
    return indexOfElement(targetBytes, 0);
  }

  @Override public long indexOfElement(ByteString targetBytes, long fromIndex) throws IOException {
    if (closed) throw new IllegalStateException("closed");
    while (fromIndex >= buffer.size) {
      if (source.read(buffer, Segment.SIZE) == -1) return -1L;
    }
    long index;
    while ((index = buffer.indexOfElement(targetBytes, fromIndex)) == -1) {
      fromIndex = buffer.size;
      if (source.read(buffer, Segment.SIZE) == -1) return -1L;
    }
    return index;
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
