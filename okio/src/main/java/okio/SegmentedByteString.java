/*
 * Copyright (C) 2015 Square, Inc.
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
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import static okio.Util.arrayRangeEquals;
import static okio.Util.checkOffsetAndCount;

/**
 * An immutable byte string composed of segments of byte arrays. This class exists to implement
 * efficient snapshots of buffers. It is implemented as an array of segments, plus a directory in
 * two halves that describes how the segments compose this byte string.
 *
 * <p>The first half of the directory is the cumulative byte count covered by each segment. The
 * element at {@code directory[0]} contains the number of bytes held in {@code segments[0]}; the
 * element at {@code directory[1]} contains the number of bytes held in {@code segments[0] +
 * segments[1]}, and so on. The element at {@code directory[segments.length - 1]} contains the total
 * size of this byte string. The first half of the directory is always monotonically increasing.
 *
 * <p>The second half of the directory is the offset in {@code segments} of the first content byte.
 * Bytes preceding this offset are unused, as are bytes beyond the segment's effective size.
 *
 * <p>Suppose we have a byte string, {@code [A, B, C, D, E, F, G, H, I, J, K, L, M]} that is stored
 * across three byte arrays: {@code [x, x, x, x, A, B, C, D, E, x, x, x]}, {@code [x, F, G]}, and
 * {@code [H, I, J, K, L, M, x, x, x, x, x, x]}. The three byte arrays would be stored in {@code
 * segments} in order. Since the arrays contribute 5, 2, and 6 elements respectively, the directory
 * starts with {@code [5, 7, 13} to hold the cumulative total at each position. Since the offsets
 * into the arrays are 4, 1, and 0 respectively, the directory ends with {@code 4, 1, 0]}.
 * Concatenating these two halves, the complete directory is {@code [5, 7, 13, 4, 1, 0]}.
 *
 * <p>This structure is chosen so that the segment holding a particular offset can be found by
 * binary search. We use one array rather than two for the directory as a micro-optimization.
 */
final class SegmentedByteString extends ByteString {
  transient final byte[][] segments;
  transient final int[] directory;

  SegmentedByteString(Buffer buffer, int byteCount) {
    super(null);
    checkOffsetAndCount(buffer.size, 0, byteCount);

    // Walk through the buffer to count how many segments we'll need.
    int offset = 0;
    int segmentCount = 0;
    for (Segment s = buffer.head; offset < byteCount; s = s.next) {
      if (s.limit == s.pos) {
        throw new AssertionError("s.limit == s.pos"); // Empty segment. This should not happen!
      }
      offset += s.limit - s.pos;
      segmentCount++;
    }

    // Walk through the buffer again to assign segments and build the directory.
    this.segments = new byte[segmentCount][];
    this.directory = new int[segmentCount * 2];
    offset = 0;
    segmentCount = 0;
    for (Segment s = buffer.head; offset < byteCount; s = s.next) {
      segments[segmentCount] = s.data;
      offset += s.limit - s.pos;
      if (offset > byteCount) {
        offset = byteCount; // Despite sharing more bytes, only report having up to byteCount.
      }
      directory[segmentCount] = offset;
      directory[segmentCount + segments.length] = s.pos;
      s.shared = true;
      segmentCount++;
    }
  }

  @Override public String utf8() {
    return toByteString().utf8();
  }

  @Override public String string(Charset charset) {
    return toByteString().string(charset);
  }

  @Override public String base64() {
    return toByteString().base64();
  }

  @Override public String hex() {
    return toByteString().hex();
  }

  @Override public ByteString toAsciiLowercase() {
    return toByteString().toAsciiLowercase();
  }

  @Override public ByteString toAsciiUppercase() {
    return toByteString().toAsciiUppercase();
  }

  @Override public ByteString md5() {
    return toByteString().md5();
  }

  @Override public ByteString sha1() {
    return toByteString().sha1();
  }

  @Override public ByteString sha256() {
    return toByteString().sha256();
  }

  @Override public ByteString hmacSha1(ByteString key) {
    return toByteString().hmacSha1(key);
  }

  @Override public ByteString hmacSha256(ByteString key) {
    return toByteString().hmacSha256(key);
  }

  @Override public String base64Url() {
    return toByteString().base64Url();
  }

  @Override public ByteString substring(int beginIndex) {
    return toByteString().substring(beginIndex);
  }

  @Override public ByteString substring(int beginIndex, int endIndex) {
    return toByteString().substring(beginIndex, endIndex);
  }

  @Override public byte getByte(int pos) {
    checkOffsetAndCount(directory[segments.length - 1], pos, 1);
    int segment = segment(pos);
    int segmentOffset = segment == 0 ? 0 : directory[segment - 1];
    int segmentPos = directory[segment + segments.length];
    return segments[segment][pos - segmentOffset + segmentPos];
  }

  /** Returns the index of the segment that contains the byte at {@code pos}. */
  private int segment(int pos) {
    // Search for (pos + 1) instead of (pos) because the directory holds sizes, not indexes.
    int i = Arrays.binarySearch(directory, 0, segments.length, pos + 1);
    return i >= 0 ? i : ~i; // If i is negative, bitflip to get the insert position.
  }

  @Override public int size() {
    return directory[segments.length - 1];
  }

  @Override public byte[] toByteArray() {
    byte[] result = new byte[directory[segments.length - 1]];
    int segmentOffset = 0;
    for (int s = 0, segmentCount = segments.length; s < segmentCount; s++) {
      int segmentPos = directory[segmentCount + s];
      int nextSegmentOffset = directory[s];
      System.arraycopy(segments[s], segmentPos, result, segmentOffset,
          nextSegmentOffset - segmentOffset);
      segmentOffset = nextSegmentOffset;
    }
    return result;
  }

  @Override public ByteBuffer asByteBuffer() {
    return ByteBuffer.wrap(toByteArray()).asReadOnlyBuffer();
  }

  @Override public void write(OutputStream out) throws IOException {
    if (out == null) throw new IllegalArgumentException("out == null");
    int segmentOffset = 0;
    for (int s = 0, segmentCount = segments.length; s < segmentCount; s++) {
      int segmentPos = directory[segmentCount + s];
      int nextSegmentOffset = directory[s];
      out.write(segments[s], segmentPos, nextSegmentOffset - segmentOffset);
      segmentOffset = nextSegmentOffset;
    }
  }

  @Override void write(Buffer buffer) {
    int segmentOffset = 0;
    for (int s = 0, segmentCount = segments.length; s < segmentCount; s++) {
      int segmentPos = directory[segmentCount + s];
      int nextSegmentOffset = directory[s];
      Segment segment = new Segment(segments[s], segmentPos,
          segmentPos + nextSegmentOffset - segmentOffset);
      if (buffer.head == null) {
        buffer.head = segment.next = segment.prev = segment;
      } else {
        buffer.head.prev.push(segment);
      }
      segmentOffset = nextSegmentOffset;
    }
    buffer.size += segmentOffset;
  }

  @Override public boolean rangeEquals(
      int offset, ByteString other, int otherOffset, int byteCount) {
    if (offset < 0 || offset > size() - byteCount) return false;
    // Go segment-by-segment through this, passing arrays to other's rangeEquals().
    for (int s = segment(offset); byteCount > 0; s++) {
      int segmentOffset = s == 0 ? 0 : directory[s - 1];
      int segmentSize = directory[s] - segmentOffset;
      int stepSize = Math.min(byteCount, segmentOffset + segmentSize - offset);
      int segmentPos = directory[segments.length + s];
      int arrayOffset = offset - segmentOffset + segmentPos;
      if (!other.rangeEquals(otherOffset, segments[s], arrayOffset, stepSize)) return false;
      offset += stepSize;
      otherOffset += stepSize;
      byteCount -= stepSize;
    }
    return true;
  }

  @Override public boolean rangeEquals(int offset, byte[] other, int otherOffset, int byteCount) {
    if (offset < 0 || offset > size() - byteCount
        || otherOffset < 0 || otherOffset > other.length - byteCount) {
      return false;
    }
    // Go segment-by-segment through this, comparing ranges of arrays.
    for (int s = segment(offset); byteCount > 0; s++) {
      int segmentOffset = s == 0 ? 0 : directory[s - 1];
      int segmentSize = directory[s] - segmentOffset;
      int stepSize = Math.min(byteCount, segmentOffset + segmentSize - offset);
      int segmentPos = directory[segments.length + s];
      int arrayOffset = offset - segmentOffset + segmentPos;
      if (!arrayRangeEquals(segments[s], arrayOffset, other, otherOffset, stepSize)) return false;
      offset += stepSize;
      otherOffset += stepSize;
      byteCount -= stepSize;
    }
    return true;
  }

  @Override public int indexOf(byte[] other, int fromIndex) {
    return toByteString().indexOf(other, fromIndex);
  }

  @Override public int lastIndexOf(byte[] other, int fromIndex) {
    return toByteString().lastIndexOf(other, fromIndex);
  }

  /** Returns a copy as a non-segmented byte string. */
  private ByteString toByteString() {
    return new ByteString(toByteArray());
  }

  @Override byte[] internalArray() {
    return toByteArray();
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    return o instanceof ByteString
        && ((ByteString) o).size() == size()
        && rangeEquals(0, ((ByteString) o), 0, size());
  }

  @Override public int hashCode() {
    int result = hashCode;
    if (result != 0) return result;

    // Equivalent to Arrays.hashCode(toByteArray()).
    result = 1;
    int segmentOffset = 0;
    for (int s = 0, segmentCount = segments.length; s < segmentCount; s++) {
      byte[] segment = segments[s];
      int segmentPos = directory[segmentCount + s];
      int nextSegmentOffset = directory[s];
      int segmentSize = nextSegmentOffset - segmentOffset;
      for (int i = segmentPos, limit = segmentPos + segmentSize; i < limit; i++) {
        result = (31 * result) + segment[i];
      }
      segmentOffset = nextSegmentOffset;
    }
    return (hashCode = result);
  }

  @Override public String toString() {
    return toByteString().toString();
  }

  private Object writeReplace() {
    return toByteString();
  }
}
