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
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This test uses four timeouts of varying durations: 250ms, 500ms, 750ms and
 * 1000ms, named 'a', 'b', 'c' and 'd'.
 */
public class AsyncTimeoutTest {
  private final List<Timeout> timedOut = new CopyOnWriteArrayList<Timeout>();
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

  /** Detecting double-enters is not guaranteed. */
  @Test public void doubleEnter() throws Exception {
    a.enter();
    try {
      a.enter();
      fail();
    } catch (IllegalStateException expected) {
    }
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
    try {
      timeoutSink.write(null, 0);
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
      timeoutSource.read(null, 0);
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
    try {
      timeoutSink.write(null, 0);
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
    try {
      timeoutSink.write(null, 0);
      fail();
    } catch (IOException expected) {
      assertEquals("no timeout occurred", expected.getMessage());
    }
  }

  /** Asserts which timeouts fired, and in which order. */
  private void assertTimedOut(Timeout... expected) {
    assertEquals(Arrays.asList(expected), timedOut);
  }

  class RecordingAsyncTimeout extends AsyncTimeout {
    @Override protected void timedOut() {
      timedOut.add(this);
    }
  }
}
