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
import org.junit.Test;

import static okio.TestUtil.repeat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests solely for the behavior of RealBufferedSink's implementation. For generic
 * BufferedSink behavior use BufferedSinkTest.
 */
public final class RealBufferedSinkTest {
  @Test public void inputStreamCloses() throws Exception {
    RealBufferedSink sink = new RealBufferedSink(new Buffer());
    OutputStream out = sink.outputStream();
    out.close();
    try {
      sink.writeUtf8("Hi!");
      fail();
    } catch (IllegalStateException e) {
      assertEquals("closed", e.getMessage());
    }
  }

  @Test public void bufferedSinkEmitsTailWhenItIsComplete() throws IOException {
    Buffer sink = new Buffer();
    BufferedSink bufferedSink = new RealBufferedSink(sink);
    bufferedSink.writeUtf8(repeat('a', Segment.SIZE - 1));
    assertEquals(0, sink.size());
    bufferedSink.writeByte(0);
    assertEquals(Segment.SIZE, sink.size());
    assertEquals(0, bufferedSink.buffer().size());
  }

  @Test public void bufferedSinkEmitMultipleSegments() throws IOException {
    Buffer sink = new Buffer();
    BufferedSink bufferedSink = new RealBufferedSink(sink);
    bufferedSink.writeUtf8(repeat('a', Segment.SIZE * 4 - 1));
    assertEquals(Segment.SIZE * 3, sink.size());
    assertEquals(Segment.SIZE - 1, bufferedSink.buffer().size());
  }

  @Test public void bufferedSinkFlush() throws IOException {
    Buffer sink = new Buffer();
    BufferedSink bufferedSink = new RealBufferedSink(sink);
    bufferedSink.writeByte('a');
    assertEquals(0, sink.size());
    bufferedSink.flush();
    assertEquals(0, bufferedSink.buffer().size());
    assertEquals(1, sink.size());
  }

  @Test public void bytesEmittedToSinkWithFlush() throws Exception {
    Buffer sink = new Buffer();
    BufferedSink bufferedSink = new RealBufferedSink(sink);
    bufferedSink.writeUtf8("abc");
    bufferedSink.flush();
    assertEquals(3, sink.size());
  }

  @Test public void bytesNotEmittedToSinkWithoutFlush() throws Exception {
    Buffer sink = new Buffer();
    BufferedSink bufferedSink = new RealBufferedSink(sink);
    bufferedSink.writeUtf8("abc");
    assertEquals(0, sink.size());
  }

  @Test public void bytesEmittedToSinkWithEmit() throws Exception {
    Buffer sink = new Buffer();
    BufferedSink bufferedSink = new RealBufferedSink(sink);
    bufferedSink.writeUtf8("abc");
    bufferedSink.emit();
    assertEquals(3, sink.size());
  }

  @Test public void completeSegmentsEmitted() throws Exception {
    Buffer sink = new Buffer();
    BufferedSink bufferedSink = new RealBufferedSink(sink);
    bufferedSink.writeUtf8(repeat('a', Segment.SIZE * 3));
    assertEquals(Segment.SIZE * 3, sink.size());
  }

  @Test public void incompleteSegmentsNotEmitted() throws Exception {
    Buffer sink = new Buffer();
    BufferedSink bufferedSink = new RealBufferedSink(sink);
    bufferedSink.writeUtf8(repeat('a', Segment.SIZE * 3 - 1));
    assertEquals(Segment.SIZE * 2, sink.size());
  }

  @Test public void closeWithExceptionWhenWriting() throws IOException {
    MockSink mockSink = new MockSink();
    mockSink.scheduleThrow(0, new IOException());
    BufferedSink bufferedSink = new RealBufferedSink(mockSink);
    bufferedSink.writeByte('a');
    try {
      bufferedSink.close();
      fail();
    } catch (IOException expected) {
    }
    mockSink.assertLog("write(Buffer[size=1 data=61], 1)", "close()");
  }

  @Test public void closeWithExceptionWhenClosing() throws IOException {
    MockSink mockSink = new MockSink();
    mockSink.scheduleThrow(1, new IOException());
    BufferedSink bufferedSink = new RealBufferedSink(mockSink);
    bufferedSink.writeByte('a');
    try {
      bufferedSink.close();
      fail();
    } catch (IOException expected) {
    }
    mockSink.assertLog("write(Buffer[size=1 data=61], 1)", "close()");
  }

  @Test public void closeWithExceptionWhenWritingAndClosing() throws IOException {
    MockSink mockSink = new MockSink();
    mockSink.scheduleThrow(0, new IOException("first"));
    mockSink.scheduleThrow(1, new IOException("second"));
    BufferedSink bufferedSink = new RealBufferedSink(mockSink);
    bufferedSink.writeByte('a');
    try {
      bufferedSink.close();
      fail();
    } catch (IOException expected) {
      assertEquals("first", expected.getMessage());
    }
    mockSink.assertLog("write(Buffer[size=1 data=61], 1)", "close()");
  }

  @Test public void operationsAfterClose() throws IOException {
    MockSink mockSink = new MockSink();
    BufferedSink bufferedSink = new RealBufferedSink(mockSink);
    bufferedSink.writeByte('a');
    bufferedSink.close();

    // Test a sample set of methods.
    try {
      bufferedSink.writeByte('a');
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSink.write(new byte[10]);
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSink.emitCompleteSegments();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSink.emit();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSink.flush();
      fail();
    } catch (IllegalStateException expected) {
    }

    // Test a sample set of methods on the OutputStream.
    OutputStream os = bufferedSink.outputStream();
    try {
      os.write('a');
      fail();
    } catch (IOException expected) {
    }

    try {
      os.write(new byte[10]);
      fail();
    } catch (IOException expected) {
    }

    // Permitted
    os.flush();
  }

  @Test public void writeAll() throws IOException {
    MockSink mockSink = new MockSink();
    BufferedSink bufferedSink = Okio.buffer(mockSink);

    bufferedSink.buffer().writeUtf8("abc");
    assertEquals(3, bufferedSink.writeAll(new Buffer().writeUtf8("def")));

    assertEquals(6, bufferedSink.buffer().size());
    assertEquals("abcdef", bufferedSink.buffer().readUtf8(6));
    mockSink.assertLog(); // No writes.
 }

  @Test public void writeAllExhausted() throws IOException {
    MockSink mockSink = new MockSink();
    BufferedSink bufferedSink = Okio.buffer(mockSink);

    assertEquals(0, bufferedSink.writeAll(new Buffer()));
    assertEquals(0, bufferedSink.buffer().size());
    mockSink.assertLog(); // No writes.
 }

  @Test public void writeAllWritesOneSegmentAtATime() throws IOException {
    Buffer write1 = new Buffer().writeUtf8(TestUtil.repeat('a', Segment.SIZE));
    Buffer write2 = new Buffer().writeUtf8(TestUtil.repeat('b', Segment.SIZE));
    Buffer write3 = new Buffer().writeUtf8(TestUtil.repeat('c', Segment.SIZE));

    Buffer source = new Buffer().writeUtf8(""
        + TestUtil.repeat('a', Segment.SIZE)
        + TestUtil.repeat('b', Segment.SIZE)
        + TestUtil.repeat('c', Segment.SIZE));

    MockSink mockSink = new MockSink();
    BufferedSink bufferedSink = Okio.buffer(mockSink);
    assertEquals(Segment.SIZE * 3, bufferedSink.writeAll(source));

    mockSink.assertLog(
        "write(" + write1 + ", " + write1.size() + ")",
        "write(" + write2 + ", " + write2.size() + ")",
        "write(" + write3 + ", " + write3.size() + ")");
 }
}
