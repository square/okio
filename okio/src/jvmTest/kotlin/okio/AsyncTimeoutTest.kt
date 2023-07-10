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

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static okio.TestUtil.bufferWithRandomSegmentLayout;
import static org.junit.Assert.*;

/**
 * This test uses four timeouts of varying durations: 250ms, 500ms, 750ms and
 * 1000ms, named 'a', 'b', 'c' and 'd'.
 */
public final class AsyncTimeoutTest {
  private final BlockingDeque<AsyncTimeout> timedOut = new LinkedBlockingDeque<>();
  private final AsyncTimeout a = new RecordingAsyncTimeout();
  private final AsyncTimeout b = new RecordingAsyncTimeout();
  private final AsyncTimeout c = new RecordingAsyncTimeout();
  private final AsyncTimeout d = new RecordingAsyncTimeout();

  @Before public void setUp() throws Exception {
    a.timeout( 250, TimeUnit.MILLISECONDS);
    b.timeout( 500, TimeUnit.MILLISECONDS);
    c.timeout( 750, TimeUnit.MILLISECONDS);
    d.timeout(1000, TimeUnit.MILLISECONDS);
  }

  @Test public void zeroTimeoutIsNoTimeout() throws Exception {
    AsyncTimeout timeout = new RecordingAsyncTimeout();
    timeout.timeout(0, TimeUnit.MILLISECONDS);
    timeout.enter();
    Thread.sleep(250);
    assertFalse(timeout.exit());
    assertTimedOut();
  }

  @Test public void singleInstanceTimedOut() throws Exception {
    a.enter();
    Thread.sleep(500);
    assertTrue(a.exit());
    assertTimedOut(a);
  }

  @Test public void singleInstanceNotTimedOut() throws Exception {
    b.enter();
    Thread.sleep(250);
    b.exit();
    assertFalse(b.exit());
    assertTimedOut();
  }

  @Test public void instancesAddedAtEnd() throws Exception {
    a.enter();
    b.enter();
    c.enter();
    d.enter();
    Thread.sleep(1250);
    assertTrue(a.exit());
    assertTrue(b.exit());
    assertTrue(c.exit());
    assertTrue(d.exit());
    assertTimedOut(a, b, c, d);
  }

  @Test public void instancesAddedAtFront() throws Exception {
    d.enter();
    c.enter();
    b.enter();
    a.enter();
    Thread.sleep(1250);
    assertTrue(d.exit());
    assertTrue(c.exit());
    assertTrue(b.exit());
    assertTrue(a.exit());
    assertTimedOut(a, b, c, d);
  }

  @Test public void instancesRemovedAtFront() throws Exception {
    a.enter();
    b.enter();
    c.enter();
    d.enter();
    assertFalse(a.exit());
    assertFalse(b.exit());
    assertFalse(c.exit());
    assertFalse(d.exit());
    assertTimedOut();
  }

  @Test public void instancesRemovedAtEnd() throws Exception {
    a.enter();
    b.enter();
    c.enter();
    d.enter();
    assertFalse(d.exit());
    assertFalse(c.exit());
    assertFalse(b.exit());
    assertFalse(a.exit());
    assertTimedOut();
  }

  @Test public void doubleEnter() throws Exception {
    a.enter();
    try {
      a.enter();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test public void reEnter() throws Exception {
    a.timeout(10, SECONDS);
    a.enter();
    assertFalse(a.exit());
    a.enter();
    assertFalse(a.exit());
  }

  @Test public void reEnterAfterTimeout() throws Exception {
    a.timeout(1, MILLISECONDS);
    a.enter();
    assertSame(a, timedOut.take());
    assertTrue(a.exit());
    a.enter();
    assertFalse(a.exit());
  }

  @Test public void deadlineOnly() throws Exception {
    RecordingAsyncTimeout timeout = new RecordingAsyncTimeout();
    timeout.deadline(250, TimeUnit.MILLISECONDS);
    timeout.enter();
    Thread.sleep(500);
    assertTrue(timeout.exit());
    assertTimedOut(timeout);
  }

  @Test public void deadlineBeforeTimeout() throws Exception {
    RecordingAsyncTimeout timeout = new RecordingAsyncTimeout();
    timeout.deadline(250, TimeUnit.MILLISECONDS);
    timeout.timeout(750, TimeUnit.MILLISECONDS);
    timeout.enter();
    Thread.sleep(500);
    assertTrue(timeout.exit());
    assertTimedOut(timeout);
  }

  @Test public void deadlineAfterTimeout() throws Exception {
    RecordingAsyncTimeout timeout = new RecordingAsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    timeout.deadline(750, TimeUnit.MILLISECONDS);
    timeout.enter();
    Thread.sleep(500);
    assertTrue(timeout.exit());
    assertTimedOut(timeout);
  }

  @Test public void deadlineStartsBeforeEnter() throws Exception {
    RecordingAsyncTimeout timeout = new RecordingAsyncTimeout();
    timeout.deadline(500, TimeUnit.MILLISECONDS);
    Thread.sleep(500);
    timeout.enter();
    Thread.sleep(250);
    assertTrue(timeout.exit());
    assertTimedOut(timeout);
  }

  @Test public void deadlineInThePast() throws Exception {
    RecordingAsyncTimeout timeout = new RecordingAsyncTimeout();
    timeout.deadlineNanoTime(System.nanoTime() - 1);
    timeout.enter();
    Thread.sleep(250);
    assertTrue(timeout.exit());
    assertTimedOut(timeout);
  }

  @Test public void wrappedSinkTimesOut() throws Exception {
    Sink sink = new ForwardingSink(new Buffer()) {
      @Override public void write(Buffer source, long byteCount) throws IOException {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          throw new AssertionError();
        }
      }
    };
    AsyncTimeout timeout = new AsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    Sink timeoutSink = timeout.sink(sink);
    Buffer data = new Buffer().writeUtf8("a");
    try {
      timeoutSink.write(data, 1);
      fail();
    } catch (InterruptedIOException expected) {
    }
  }

  @Test public void wrappedSinkFlushTimesOut() throws Exception {
    Sink sink = new ForwardingSink(new Buffer()) {
      @Override public void flush() throws IOException {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          throw new AssertionError();
        }
      }
    };
    AsyncTimeout timeout = new AsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    Sink timeoutSink = timeout.sink(sink);
    try {
      timeoutSink.flush();
      fail();
    } catch (InterruptedIOException expected) {
    }
  }

  @Test public void wrappedSinkCloseTimesOut() throws Exception {
    Sink sink = new ForwardingSink(new Buffer()) {
      @Override public void close() throws IOException {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          throw new AssertionError();
        }
      }
    };
    AsyncTimeout timeout = new AsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    Sink timeoutSink = timeout.sink(sink);
    try {
      timeoutSink.close();
      fail();
    } catch (InterruptedIOException expected) {
    }
  }

  @Test public void wrappedSourceTimesOut() throws Exception {
    Source source = new ForwardingSource(new Buffer()) {
      @Override public long read(Buffer sink, long byteCount) throws IOException {
        try {
          Thread.sleep(500);
          return -1;
        } catch (InterruptedException e) {
          throw new AssertionError();
        }
      }
    };
    AsyncTimeout timeout = new AsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    Source timeoutSource = timeout.source(source);
    try {
      timeoutSource.read(new Buffer(), 0);
      fail();
    } catch (InterruptedIOException expected) {
    }
  }

  @Test public void wrappedSourceCloseTimesOut() throws Exception {
    Source source = new ForwardingSource(new Buffer()) {
      @Override public void close() throws IOException {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          throw new AssertionError();
        }
      }
    };
    AsyncTimeout timeout = new AsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    Source timeoutSource = timeout.source(source);
    try {
      timeoutSource.close();
      fail();
    } catch (InterruptedIOException expected) {
    }
  }

  @Test public void wrappedThrowsWithTimeout() throws Exception {
    Sink sink = new ForwardingSink(new Buffer()) {
      @Override public void write(Buffer source, long byteCount) throws IOException {
        try {
          Thread.sleep(500);
          throw new IOException("exception and timeout");
        } catch (InterruptedException e) {
          throw new AssertionError();
        }
      }
    };
    AsyncTimeout timeout = new AsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    Sink timeoutSink = timeout.sink(sink);
    Buffer data = new Buffer().writeUtf8("a");
    try {
      timeoutSink.write(data, 1);
      fail();
    } catch (InterruptedIOException expected) {
      assertEquals("timeout", expected.getMessage());
      assertEquals("exception and timeout", expected.getCause().getMessage());
    }
  }

  @Test public void wrappedThrowsWithoutTimeout() throws Exception {
    Sink sink = new ForwardingSink(new Buffer()) {
      @Override public void write(Buffer source, long byteCount) throws IOException {
        throw new IOException("no timeout occurred");
      }
    };
    AsyncTimeout timeout = new AsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    Sink timeoutSink = timeout.sink(sink);
    Buffer data = new Buffer().writeUtf8("a");
    try {
      timeoutSink.write(data, 1);
      fail();
    } catch (IOException expected) {
      assertEquals("no timeout occurred", expected.getMessage());
    }
  }

  /**
   * We had a bug where writing a very large buffer would fail with an
   * unexpected timeout because although the sink was making steady forward
   * progress, doing it all as a single write caused a timeout.
   */
  @Ignore("Flaky")
  @Test public void sinkSplitsLargeWrites() throws Exception {
    byte[] data = new byte[512 * 1024];
    Random dice = new Random(0);
    dice.nextBytes(data);
    final Buffer source = bufferWithRandomSegmentLayout(dice, data);
    final Buffer target = new Buffer();

    Sink sink = new ForwardingSink(new Buffer()) {
      @Override public void write(Buffer source, long byteCount) throws IOException {
        try {
          Thread.sleep(byteCount / 500); // ~500 KiB/s.
          target.write(source, byteCount);
        } catch (InterruptedException e) {
          throw new AssertionError();
        }
      }
    };

    // Timeout after 250 ms of inactivity.
    AsyncTimeout timeout = new AsyncTimeout();
    timeout.timeout(250, TimeUnit.MILLISECONDS);
    Sink timeoutSink = timeout.sink(sink);

    // Transmit 500 KiB of data, which should take ~1 second. But expect no timeout!
    timeoutSink.write(source, source.size());

    // The data should all have arrived.
    assertEquals(ByteString.of(data), target.readByteString());
  }

  /** Asserts which timeouts fired, and in which order. */
  private void assertTimedOut(Timeout... expected) {
    assertEquals(Arrays.asList(expected), new ArrayList<Timeout>(timedOut));
  }

  class RecordingAsyncTimeout extends AsyncTimeout {
    @Override protected void timedOut() {
      timedOut.add(this);
    }
  }
}
