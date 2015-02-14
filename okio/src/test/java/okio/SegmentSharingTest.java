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

import org.junit.Test;

import static okio.TestUtil.assertEquivalent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Tests behavior optimized by sharing segments between buffers and byte strings. */
public final class SegmentSharingTest {
  private static final String us = TestUtil.repeat('u', Segment.SIZE / 2 - 2);
  private static final String vs = TestUtil.repeat('v', Segment.SIZE / 2 - 1);
  private static final String ws = TestUtil.repeat('w', Segment.SIZE / 2);
  private static final String xs = TestUtil.repeat('x', Segment.SIZE / 2 + 1);
  private static final String ys = TestUtil.repeat('y', Segment.SIZE / 2 + 2);
  private static final String zs = TestUtil.repeat('z', Segment.SIZE / 2 + 3);

  @Test public void snapshotOfEmptyBuffer() throws Exception {
    ByteString snapshot = new Buffer().snapshot();
    assertEquivalent(snapshot, ByteString.EMPTY);
  }

  @Test public void snapshotsAreEquivalent() throws Exception {
    ByteString byteString = concatenateBuffers(xs, ys, zs).snapshot();
    assertEquivalent(byteString, concatenateBuffers(xs, ys + zs).snapshot());
    assertEquivalent(byteString, concatenateBuffers(xs + ys + zs).snapshot());
    assertEquivalent(byteString, ByteString.encodeUtf8(xs + ys + zs));
  }

  @Test public void snapshotGetByte() throws Exception {
    ByteString byteString = concatenateBuffers(xs, ys, zs).snapshot();
    assertEquals('x', byteString.getByte(0));
    assertEquals('x', byteString.getByte(xs.length() - 1));
    assertEquals('y', byteString.getByte(xs.length()));
    assertEquals('y', byteString.getByte(xs.length() + ys.length() - 1));
    assertEquals('z', byteString.getByte(xs.length() + ys.length()));
    assertEquals('z', byteString.getByte(xs.length() + ys.length() + zs.length() - 1));
    try {
      byteString.getByte(-1);
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
    try {
      byteString.getByte(xs.length() + ys.length() + zs.length());
      fail();
    } catch (IndexOutOfBoundsException expected) {
    }
  }

  @Test public void snapshotWriteToOutputStream() throws Exception {
    ByteString byteString = concatenateBuffers(xs, ys, zs).snapshot();
    Buffer out = new Buffer();
    byteString.write(out.outputStream());
    assertEquals(xs + ys + zs, out.readUtf8());
  }

  /**
   * Snapshots share their backing byte arrays with the source buffers. Those byte arrays must not
   * be recycled, otherwise the new writer could corrupt the segment.
   */
  @Test public void snapshotSegmentsAreNotRecycled() throws Exception {
    Buffer buffer = concatenateBuffers(xs, ys, zs);
    ByteString snapshot = buffer.snapshot();
    assertEquals(xs + ys + zs, snapshot.utf8());

    // While locking the pool, confirm that clearing the buffer doesn't release its segments.
    synchronized (SegmentPool.class) {
      SegmentPool.next = null;
      SegmentPool.byteCount = 0L;
      buffer.clear();
      assertEquals(null, SegmentPool.next);
    }
  }

  /**
   * Clones share their backing byte arrays with the source buffers. Those byte arrays must not
   * be recycled, otherwise the new writer could corrupt the segment.
   */
  @Test public void cloneSegmentsAreNotRecycled() throws Exception {
    Buffer buffer = concatenateBuffers(xs, ys, zs);
    Buffer clone = buffer.clone();

    // While locking the pool, confirm that clearing the buffer doesn't release its segments.
    synchronized (SegmentPool.class) {
      SegmentPool.next = null;
      SegmentPool.byteCount = 0L;
      buffer.clear();
      assertEquals(null, SegmentPool.next);
      clone.clear();
      assertEquals(null, SegmentPool.next);
    }
  }

  @Test public void snapshotJavaSerialization() throws Exception {
    ByteString byteString = concatenateBuffers(xs, ys, zs).snapshot();
    assertEquivalent(byteString, TestUtil.reserialize(byteString));
  }

  @Test public void clonesAreEquivalent() throws Exception {
    Buffer bufferA = concatenateBuffers(xs, ys, zs);
    Buffer bufferB = bufferA.clone();
    assertEquivalent(bufferA, bufferB);
    assertEquivalent(bufferA, concatenateBuffers(xs + ys, zs));
  }

  /** Even though some segments are shared, clones can be mutated independently. */
  @Test public void mutateAfterClone() throws Exception {
    Buffer bufferA = new Buffer();
    bufferA.writeUtf8("abc");
    Buffer bufferB = bufferA.clone();
    bufferA.writeUtf8("def");
    bufferB.writeUtf8("DEF");
    assertEquals("abcdef", bufferA.readUtf8());
    assertEquals("abcDEF", bufferB.readUtf8());
  }

  @Test public void concatenateSegmentsCanCombine() throws Exception {
    Buffer bufferA = new Buffer().writeUtf8(ys).writeUtf8(us);
    assertEquals(ys, bufferA.readUtf8(ys.length()));
    Buffer bufferB = new Buffer().writeUtf8(vs).writeUtf8(ws);
    Buffer bufferC = bufferA.clone();
    bufferA.write(bufferB, vs.length());
    bufferC.writeUtf8(xs);

    assertEquals(us + vs, bufferA.readUtf8());
    assertEquals(ws, bufferB.readUtf8());
    assertEquals(us + xs, bufferC.readUtf8());
  }

  @Test public void shareAndSplit() throws Exception {
    Buffer bufferA = new Buffer().writeUtf8("xxxx");
    ByteString snapshot = bufferA.snapshot(); // Share the segment.
    Buffer bufferB = new Buffer();
    bufferB.write(bufferA, 2); // Split the shared segment in two.
    bufferB.writeUtf8("yy"); // Append to the first half of the shared segment.
    assertEquals("xxxx", snapshot.utf8());
  }

  @Test public void appendSnapshotToEmptyBuffer() throws Exception {
    Buffer bufferA = concatenateBuffers(xs, ys);
    ByteString snapshot = bufferA.snapshot();
    Buffer bufferB = new Buffer();
    bufferB.write(snapshot);
    assertEquivalent(bufferB, bufferA);
  }

  @Test public void appendSnapshotToNonEmptyBuffer() throws Exception {
    Buffer bufferA = concatenateBuffers(xs, ys);
    ByteString snapshot = bufferA.snapshot();
    Buffer bufferB = new Buffer().writeUtf8(us);
    bufferB.write(snapshot);
    assertEquivalent(bufferB, new Buffer().writeUtf8(us + xs + ys));
  }

  @Test public void copyToSegmentSharing() throws Exception {
    Buffer bufferA = concatenateBuffers(ws, xs + "aaaa", ys, "bbbb" + zs);
    Buffer bufferB = concatenateBuffers(us);
    bufferA.copyTo(bufferB, ws.length() + xs.length(), 4 + ys.length() + 4);
    assertEquivalent(bufferB, new Buffer().writeUtf8(us + "aaaa" + ys + "bbbb"));
  }

  /**
   * Returns a new buffer containing the contents of {@code segments}, attempting to isolate each
   * string to its own segment in the returned buffer.
   */
  public Buffer concatenateBuffers(String... segments) throws Exception {
    Buffer result = new Buffer();
    for (String s : segments) {
      int offsetInSegment = s.length() < Segment.SIZE ? (Segment.SIZE - s.length()) / 2 : 0;
      Buffer buffer = new Buffer();
      buffer.writeUtf8(TestUtil.repeat('_', offsetInSegment));
      buffer.writeUtf8(s);
      buffer.skip(offsetInSegment);
      result.write(buffer, buffer.size);
    }
    return result;
  }
}
