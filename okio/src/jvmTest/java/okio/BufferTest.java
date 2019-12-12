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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Test;

import static java.util.Arrays.asList;
import static kotlin.text.Charsets.UTF_8;
import static kotlin.text.StringsKt.repeat;
import static okio.TestUtil.SEGMENT_POOL_MAX_SIZE;
import static okio.TestUtil.SEGMENT_SIZE;
import static okio.TestUtil.assertNoEmptySegments;
import static okio.TestUtil.bufferWithRandomSegmentLayout;
import static okio.TestUtil.segmentPoolByteCount;
import static okio.TestUtil.segmentSizes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests solely for the behavior of Buffer's implementation. For generic BufferedSink or
 * BufferedSource behavior use BufferedSinkTest or BufferedSourceTest, respectively.
 */
public final class BufferTest {
  @Test public void readAndWriteUtf8() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("ab");
    assertEquals(2, buffer.size());
    buffer.writeUtf8("cdef");
    assertEquals(6, buffer.size());
    assertEquals("abcd", buffer.readUtf8(4));
    assertEquals(2, buffer.size());
    assertEquals("ef", buffer.readUtf8(2));
    assertEquals(0, buffer.size());
    try {
      buffer.readUtf8(1);
      fail();
    } catch (EOFException expected) {
    }
  }

  /** Buffer's toString is the same as ByteString's. */
  @Test public void bufferToString() {
    assertEquals("[size=0]", new Buffer().toString());
    assertEquals("[text=a\\r\\nb\\nc\\rd\\\\e]",
        new Buffer().writeUtf8("a\r\nb\nc\rd\\e").toString());
    assertEquals("[text=Tyrannosaur]",
        new Buffer().writeUtf8("Tyrannosaur").toString());
    assertEquals("[text=təˈranəˌsôr]", new Buffer()
        .write(ByteString.decodeHex("74c999cb8872616ec999cb8c73c3b472"))
        .toString());
    assertEquals("[hex=0000000000000000000000000000000000000000000000000000000000000000000000000000"
        + "0000000000000000000000000000000000000000000000000000]",
        new Buffer().write(new byte[64]).toString());
  }

  @Test public void multipleSegmentBuffers() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8(repeat("a", 1000));
    buffer.writeUtf8(repeat("b", 2500));
    buffer.writeUtf8(repeat("c", 5000));
    buffer.writeUtf8(repeat("d", 10000));
    buffer.writeUtf8(repeat("e", 25000));
    buffer.writeUtf8(repeat("f", 50000));

    assertEquals(repeat("a", 999), buffer.readUtf8(999)); // a...a
    assertEquals("a" + repeat("b", 2500) + "c", buffer.readUtf8(2502)); // ab...bc
    assertEquals(repeat("c", 4998), buffer.readUtf8(4998)); // c...c
    assertEquals("c" + repeat("d", 10000) + "e", buffer.readUtf8(10002)); // cd...de
    assertEquals(repeat("e", 24998), buffer.readUtf8(24998)); // e...e
    assertEquals("e" + repeat("f", 50000), buffer.readUtf8(50001)); // ef...f
    assertEquals(0, buffer.size());
  }

  @Test public void fillAndDrainPool() throws Exception {
    Buffer buffer = new Buffer();

    // Take 2 * MAX_SIZE segments. This will drain the pool, even if other tests filled it.
    buffer.write(new byte[(int) SEGMENT_POOL_MAX_SIZE]);
    buffer.write(new byte[(int) SEGMENT_POOL_MAX_SIZE]);
    assertEquals(0, segmentPoolByteCount());

    // Recycle MAX_SIZE segments. They're all in the pool.
    buffer.skip(SEGMENT_POOL_MAX_SIZE);
    assertEquals(SEGMENT_POOL_MAX_SIZE, segmentPoolByteCount());

    // Recycle MAX_SIZE more segments. The pool is full so they get garbage collected.
    buffer.skip(SEGMENT_POOL_MAX_SIZE);
    assertEquals(SEGMENT_POOL_MAX_SIZE, segmentPoolByteCount());

    // Take MAX_SIZE segments to drain the pool.
    buffer.write(new byte[(int) SEGMENT_POOL_MAX_SIZE]);
    assertEquals(0, segmentPoolByteCount());

    // Take MAX_SIZE more segments. The pool is drained so these will need to be allocated.
    buffer.write(new byte[(int) SEGMENT_POOL_MAX_SIZE]);
    assertEquals(0, segmentPoolByteCount());
  }

  @Test public void moveBytesBetweenBuffersShareSegment() throws Exception {
    int size = (SEGMENT_SIZE / 2) - 1;
    List<Integer> segmentSizes = moveBytesBetweenBuffers(repeat("a", size), repeat("b", size));
    assertEquals(asList(size * 2), segmentSizes);
  }

  @Test public void moveBytesBetweenBuffersReassignSegment() throws Exception {
    int size = (SEGMENT_SIZE / 2) + 1;
    List<Integer> segmentSizes = moveBytesBetweenBuffers(repeat("a", size), repeat("b", size));
    assertEquals(asList(size, size), segmentSizes);
  }

  @Test public void moveBytesBetweenBuffersMultipleSegments() throws Exception {
    int size = 3 * SEGMENT_SIZE + 1;
    List<Integer> segmentSizes = moveBytesBetweenBuffers(repeat("a", size), repeat("b", size));
    assertEquals(asList(SEGMENT_SIZE, SEGMENT_SIZE, SEGMENT_SIZE, 1,
        SEGMENT_SIZE, SEGMENT_SIZE, SEGMENT_SIZE, 1), segmentSizes);
  }

  private List<Integer> moveBytesBetweenBuffers(String... contents) throws IOException {
    StringBuilder expected = new StringBuilder();
    Buffer buffer = new Buffer();
    for (String s : contents) {
      Buffer source = new Buffer();
      source.writeUtf8(s);
      buffer.writeAll(source);
      expected.append(s);
    }
    List<Integer> segmentSizes = segmentSizes(buffer);
    assertEquals(expected.toString(), buffer.readUtf8(expected.length()));
    return segmentSizes;
  }

  /** The big part of source's first segment is being moved. */
  @Test public void writeSplitSourceBufferLeft() {
    int writeSize = SEGMENT_SIZE / 2 + 1;

    Buffer sink = new Buffer();
    sink.writeUtf8(repeat("b", SEGMENT_SIZE - 10));

    Buffer source = new Buffer();
    source.writeUtf8(repeat("a", SEGMENT_SIZE * 2));
    sink.write(source, writeSize);

    assertEquals(asList(SEGMENT_SIZE - 10, writeSize), segmentSizes(sink));
    assertEquals(asList(SEGMENT_SIZE - writeSize, SEGMENT_SIZE), segmentSizes(source));
  }

  /** The big part of source's first segment is staying put. */
  @Test public void writeSplitSourceBufferRight() {
    int writeSize = SEGMENT_SIZE / 2 - 1;

    Buffer sink = new Buffer();
    sink.writeUtf8(repeat("b", SEGMENT_SIZE - 10));

    Buffer source = new Buffer();
    source.writeUtf8(repeat("a", SEGMENT_SIZE * 2));
    sink.write(source, writeSize);

    assertEquals(asList(SEGMENT_SIZE - 10, writeSize), segmentSizes(sink));
    assertEquals(asList(SEGMENT_SIZE - writeSize, SEGMENT_SIZE), segmentSizes(source));
  }

  @Test public void writePrefixDoesntSplit() {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat("b", 10));

    Buffer source = new Buffer();
    source.writeUtf8(repeat("a", SEGMENT_SIZE * 2));
    sink.write(source, 20);

    assertEquals(asList(30), segmentSizes(sink));
    assertEquals(asList(SEGMENT_SIZE - 20, SEGMENT_SIZE), segmentSizes(source));
    assertEquals(30, sink.size());
    assertEquals(SEGMENT_SIZE * 2 - 20, source.size());
  }

  @Test public void writePrefixDoesntSplitButRequiresCompact() throws Exception {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat("b", SEGMENT_SIZE - 10)); // limit = size - 10
    sink.readUtf8(SEGMENT_SIZE - 20); // pos = size = 20

    Buffer source = new Buffer();
    source.writeUtf8(repeat("a", SEGMENT_SIZE * 2));
    sink.write(source, 20);

    assertEquals(asList(30), segmentSizes(sink));
    assertEquals(asList(SEGMENT_SIZE - 20, SEGMENT_SIZE), segmentSizes(source));
    assertEquals(30, sink.size());
    assertEquals(SEGMENT_SIZE * 2 - 20, source.size());
  }

  @Test public void copyToSpanningSegments() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8(repeat("a", SEGMENT_SIZE * 2));
    source.writeUtf8(repeat("b", SEGMENT_SIZE * 2));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    source.copyTo(out, 10, SEGMENT_SIZE * 3);

    assertEquals(repeat("a", SEGMENT_SIZE * 2 - 10) + repeat("b", SEGMENT_SIZE + 10),
        out.toString());
    assertEquals(repeat("a", SEGMENT_SIZE * 2) + repeat("b", SEGMENT_SIZE * 2),
        source.readUtf8(SEGMENT_SIZE * 4));
  }

  @Test public void copyToStream() throws Exception {
    Buffer buffer = new Buffer().writeUtf8("hello, world!");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    buffer.copyTo(out);
    String outString = new String(out.toByteArray(), UTF_8);
    assertEquals("hello, world!", outString);
    assertEquals("hello, world!", buffer.readUtf8());
  }

  @Test public void writeToSpanningSegments() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8(repeat("a", SEGMENT_SIZE * 2));
    buffer.writeUtf8(repeat("b", SEGMENT_SIZE * 2));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    buffer.skip(10);
    buffer.writeTo(out, SEGMENT_SIZE * 3);

    assertEquals(repeat("a", SEGMENT_SIZE * 2 - 10) + repeat("b", SEGMENT_SIZE + 10),
        out.toString());
    assertEquals(repeat("b", SEGMENT_SIZE - 10), buffer.readUtf8(buffer.size()));
  }

  @Test public void writeToStream() throws Exception {
    Buffer buffer = new Buffer().writeUtf8("hello, world!");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    buffer.writeTo(out);
    String outString = new String(out.toByteArray(), UTF_8);
    assertEquals("hello, world!", outString);
    assertEquals(0, buffer.size());
  }

  @Test public void readFromStream() throws Exception {
    InputStream in = new ByteArrayInputStream("hello, world!".getBytes(UTF_8));
    Buffer buffer = new Buffer();
    buffer.readFrom(in);
    String out = buffer.readUtf8();
    assertEquals("hello, world!", out);
  }

  @Test public void readFromSpanningSegments() throws Exception {
    InputStream in = new ByteArrayInputStream("hello, world!".getBytes(UTF_8));
    Buffer buffer = new Buffer().writeUtf8(repeat("a", SEGMENT_SIZE - 10));
    buffer.readFrom(in);
    String out = buffer.readUtf8();
    assertEquals(repeat("a", SEGMENT_SIZE - 10) + "hello, world!", out);
  }

  @Test public void readFromStreamWithCount() throws Exception {
    InputStream in = new ByteArrayInputStream("hello, world!".getBytes(UTF_8));
    Buffer buffer = new Buffer();
    buffer.readFrom(in, 10);
    String out = buffer.readUtf8();
    assertEquals("hello, wor", out);
  }

  @Test public void readFromDoesNotLeaveEmptyTailSegment() throws IOException {
    Buffer buffer = new Buffer();
    buffer.readFrom(new ByteArrayInputStream(new byte[SEGMENT_SIZE]));
    assertNoEmptySegments(buffer);
  }

  @Test public void moveAllRequestedBytesWithRead() throws Exception {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat("a", 10));

    Buffer source = new Buffer();
    source.writeUtf8(repeat("b", 15));

    assertEquals(10, source.read(sink, 10));
    assertEquals(20, sink.size());
    assertEquals(5, source.size());
    assertEquals(repeat("a", 10) + repeat("b", 10), sink.readUtf8(20));
  }

  @Test public void moveFewerThanRequestedBytesWithRead() throws Exception {
    Buffer sink = new Buffer();
    sink.writeUtf8(repeat("a", 10));

    Buffer source = new Buffer();
    source.writeUtf8(repeat("b", 20));

    assertEquals(20, source.read(sink, 25));
    assertEquals(30, sink.size());
    assertEquals(0, source.size());
    assertEquals(repeat("a", 10) + repeat("b", 20), sink.readUtf8(30));
  }

  @Test public void indexOfWithOffset() {
    Buffer buffer = new Buffer();
    int halfSegment = SEGMENT_SIZE / 2;
    buffer.writeUtf8(repeat("a", halfSegment));
    buffer.writeUtf8(repeat("b", halfSegment));
    buffer.writeUtf8(repeat("c", halfSegment));
    buffer.writeUtf8(repeat("d", halfSegment));
    assertEquals(0, buffer.indexOf((byte) 'a', 0));
    assertEquals(halfSegment - 1, buffer.indexOf((byte) 'a', halfSegment - 1));
    assertEquals(halfSegment, buffer.indexOf((byte) 'b', halfSegment - 1));
    assertEquals(halfSegment * 2, buffer.indexOf((byte) 'c', halfSegment - 1));
    assertEquals(halfSegment * 3, buffer.indexOf((byte) 'd', halfSegment - 1));
    assertEquals(halfSegment * 3, buffer.indexOf((byte) 'd', halfSegment * 2));
    assertEquals(halfSegment * 3, buffer.indexOf((byte) 'd', halfSegment * 3));
    assertEquals(halfSegment * 4 - 1, buffer.indexOf((byte) 'd', halfSegment * 4 - 1));
  }

  @Test public void byteAt() {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("a");
    buffer.writeUtf8(repeat("b", SEGMENT_SIZE));
    buffer.writeUtf8("c");
    assertEquals('a', buffer.getByte(0));
    assertEquals('a', buffer.getByte(0)); // getByte doesn't mutate!
    assertEquals('c', buffer.getByte(buffer.size() - 1));
    assertEquals('b', buffer.getByte(buffer.size() - 2));
    assertEquals('b', buffer.getByte(buffer.size() - 3));
  }

  @Test public void getByteOfEmptyBuffer() {
    Buffer buffer = new Buffer();
    try {
      buffer.getByte(0);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  @Test public void writePrefixToEmptyBuffer() throws IOException {
    Buffer sink = new Buffer();
    Buffer source = new Buffer();
    source.writeUtf8("abcd");
    sink.write(source, 2);
    assertEquals("ab", sink.readUtf8(2));
  }

  @Test public void cloneDoesNotObserveWritesToOriginal() {
    Buffer original = new Buffer();
    Buffer clone = original.clone();
    original.writeUtf8("abc");
    assertEquals(0, clone.size());
  }

  @Test public void cloneDoesNotObserveReadsFromOriginal() throws Exception {
    Buffer original = new Buffer();
    original.writeUtf8("abc");
    Buffer clone = original.clone();
    assertEquals("abc", original.readUtf8(3));
    assertEquals(3, clone.size());
    assertEquals("ab", clone.readUtf8(2));
  }

  @Test public void originalDoesNotObserveWritesToClone() {
    Buffer original = new Buffer();
    Buffer clone = original.clone();
    clone.writeUtf8("abc");
    assertEquals(0, original.size());
  }

  @Test public void originalDoesNotObserveReadsFromClone() throws Exception {
    Buffer original = new Buffer();
    original.writeUtf8("abc");
    Buffer clone = original.clone();
    assertEquals("abc", clone.readUtf8(3));
    assertEquals(3, original.size());
    assertEquals("ab", original.readUtf8(2));
  }

  @Test public void cloneMultipleSegments() throws Exception {
    Buffer original = new Buffer();
    original.writeUtf8(repeat("a", SEGMENT_SIZE * 3));
    Buffer clone = original.clone();
    original.writeUtf8(repeat("b", SEGMENT_SIZE * 3));
    clone.writeUtf8(repeat("c", SEGMENT_SIZE * 3));

    assertEquals(repeat("a", SEGMENT_SIZE * 3) + repeat("b", SEGMENT_SIZE * 3),
        original.readUtf8(SEGMENT_SIZE * 6));
    assertEquals(repeat("a", SEGMENT_SIZE * 3) + repeat("c", SEGMENT_SIZE * 3),
        clone.readUtf8(SEGMENT_SIZE * 6));
  }

  @Test public void equalsAndHashCodeEmpty() {
    Buffer a = new Buffer();
    Buffer b = new Buffer();
    assertTrue(a.equals(b));
    assertTrue(a.hashCode() == b.hashCode());
  }

  @Test public void equalsAndHashCode() throws Exception {
    Buffer a = new Buffer().writeUtf8("dog");
    Buffer b = new Buffer().writeUtf8("hotdog");
    assertFalse(a.equals(b));
    assertFalse(a.hashCode() == b.hashCode());

    b.readUtf8(3); // Leaves b containing 'dog'.
    assertTrue(a.equals(b));
    assertTrue(a.hashCode() == b.hashCode());
  }

  @Test public void equalsAndHashCodeSpanningSegments() throws Exception {
    byte[] data = new byte[1024 * 1024];
    Random dice = new Random(0);
    dice.nextBytes(data);

    Buffer a = bufferWithRandomSegmentLayout(dice, data);
    Buffer b = bufferWithRandomSegmentLayout(dice, data);
    assertTrue(a.equals(b));
    assertTrue(a.hashCode() == b.hashCode());

    data[data.length / 2]++; // Change a single byte.
    Buffer c = bufferWithRandomSegmentLayout(dice, data);
    assertFalse(a.equals(c));
    assertFalse(a.hashCode() == c.hashCode());
  }

  @Test public void bufferInputStreamByteByByte() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("abc");

    InputStream in = source.inputStream();
    assertEquals(3, in.available());
    assertEquals('a', in.read());
    assertEquals('b', in.read());
    assertEquals('c', in.read());
    assertEquals(-1, in.read());
    assertEquals(0, in.available());
  }

  @Test public void bufferInputStreamBulkReads() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("abc");

    byte[] byteArray = new byte[4];

    Arrays.fill(byteArray, (byte) -5);
    InputStream in = source.inputStream();
    assertEquals(3, in.read(byteArray));
    assertEquals("[97, 98, 99, -5]", Arrays.toString(byteArray));

    Arrays.fill(byteArray, (byte) -7);
    assertEquals(-1, in.read(byteArray));
    assertEquals("[-7, -7, -7, -7]", Arrays.toString(byteArray));
  }

  /**
   * When writing data that's already buffered, there's no reason to page the
   * data by segment.
   */
  @Test public void readAllWritesAllSegmentsAtOnce() throws Exception {
    Buffer write1 = new Buffer().writeUtf8(""
        + repeat("a", SEGMENT_SIZE)
        + repeat("b", SEGMENT_SIZE)
        + repeat("c", SEGMENT_SIZE));

    Buffer source = new Buffer().writeUtf8(""
        + repeat("a", SEGMENT_SIZE)
        + repeat("b", SEGMENT_SIZE)
        + repeat("c", SEGMENT_SIZE));

    MockSink mockSink = new MockSink();

    assertEquals(SEGMENT_SIZE * 3, source.readAll(mockSink));
    assertEquals(0, source.size());
    mockSink.assertLog("write(" + write1 + ", " + write1.size() + ")");
  }

  @Test public void writeAllMultipleSegments() throws Exception {
    Buffer source = new Buffer().writeUtf8(repeat("a", SEGMENT_SIZE * 3));
    Buffer sink = new Buffer();

    assertEquals(SEGMENT_SIZE * 3, sink.writeAll(source));
    assertEquals(0, source.size());
    assertEquals(repeat("a", SEGMENT_SIZE * 3), sink.readUtf8());
  }

  @Test public void copyTo() {
    Buffer source = new Buffer();
    source.writeUtf8("party");

    Buffer target = new Buffer();
    source.copyTo(target, 1, 3);

    assertEquals("art", target.readUtf8());
    assertEquals("party", source.readUtf8());
  }

  @Test public void copyToOnSegmentBoundary() {
    String as = repeat("a", SEGMENT_SIZE);
    String bs = repeat("b", SEGMENT_SIZE);
    String cs = repeat("c", SEGMENT_SIZE);
    String ds = repeat("d", SEGMENT_SIZE);

    Buffer source = new Buffer();
    source.writeUtf8(as);
    source.writeUtf8(bs);
    source.writeUtf8(cs);

    Buffer target = new Buffer();
    target.writeUtf8(ds);

    source.copyTo(target, as.length(), bs.length() + cs.length());
    assertEquals(ds + bs + cs, target.readUtf8());
  }

  @Test public void copyToOffSegmentBoundary() {
    String as = repeat("a", SEGMENT_SIZE - 1);
    String bs = repeat("b", SEGMENT_SIZE + 2);
    String cs = repeat("c", SEGMENT_SIZE - 4);
    String ds = repeat("d", SEGMENT_SIZE + 8);

    Buffer source = new Buffer();
    source.writeUtf8(as);
    source.writeUtf8(bs);
    source.writeUtf8(cs);

    Buffer target = new Buffer();
    target.writeUtf8(ds);

    source.copyTo(target, as.length(), bs.length() + cs.length());
    assertEquals(ds + bs + cs, target.readUtf8());
  }

  @Test public void copyToSourceAndTargetCanBeTheSame() {
    String as = repeat("a", SEGMENT_SIZE);
    String bs = repeat("b", SEGMENT_SIZE);

    Buffer source = new Buffer();
    source.writeUtf8(as);
    source.writeUtf8(bs);

    source.copyTo(source, 0, source.size());
    assertEquals(as + bs + as + bs, source.readUtf8());
  }

  @Test public void copyToEmptySource() {
    Buffer source = new Buffer();
    Buffer target = new Buffer().writeUtf8("aaa");
    source.copyTo(target, 0L, 0L);
    assertEquals("", source.readUtf8());
    assertEquals("aaa", target.readUtf8());
  }

  @Test public void copyToEmptyTarget() {
    Buffer source = new Buffer().writeUtf8("aaa");
    Buffer target = new Buffer();
    source.copyTo(target, 0L, 3L);
    assertEquals("aaa", source.readUtf8());
    assertEquals("aaa", target.readUtf8());
  }

  @Test public void snapshotReportsAccurateSize() {
    Buffer buf = new Buffer().write(new byte[] { 0, 1, 2, 3 });
    assertEquals(1, buf.snapshot(1).size());
  }
}
