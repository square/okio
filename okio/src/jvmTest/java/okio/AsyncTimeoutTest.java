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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static okio.TestUtil.bufferWithRandomSegmentLayout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This test uses four timeouts of varying durations: 250ms, 500ms, 750ms and
 * 1000ms, named 'a', 'b', 'c' and 'd'.
 */
public final class AsyncTimeoutTest {
  private final List<Timeout> timedOut = new CopyOnWriteArrayList<>();
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

  @Test public void concurrentEnter() throws Exception {
    final int concurrentTests = 8;
    final int testIterations = 10000;
    final int concurrentTestThreads = 2;
    final ExecutorService executor = newFixedThreadPool(concurrentTests * (1 + concurrentTestThreads));
    try {
      final CyclicBarrier testBarrier = new CyclicBarrier(concurrentTests);
      List<Future<?>> testFutures = new ArrayList<>();
      for (int test = 0; test < concurrentTests; test++) {
        testFutures.add(executor.submit(new Callable<Void>() {
          @Override
          public Void call()
                  throws Exception
          {
            testBarrier.await(10, SECONDS);
            for (int iteration = 0; iteration < testIterations; iteration++) {
              if (iteration % 100 == 0) {
                System.out.println("Running iteration " + iteration);
              }
              concurrentEnterIteration(executor, concurrentTestThreads);
            }
            return null;
          }
        }));
      }
      for (Future<?> testFuture : testFutures) {
        // A timeout to obtain the future may mean the test dead locked
        testFuture.get(30, SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private void concurrentEnterIteration(ExecutorService executor, int concurrentTestThreads) throws Exception {
    final AsyncTimeout a = new AsyncTimeout();
    a.timeout(10, SECONDS);
    final CyclicBarrier barrier = new CyclicBarrier(concurrentTestThreads);
    List<Future<Void>> futures = new ArrayList<>();
    for (int thread = 0; thread < concurrentTestThreads; thread++) {
      futures.add(executor.submit(new Callable<Void>() {
        @Override
        public Void call()
                throws Exception
        {
          barrier.await(10, SECONDS);
          a.enter();
          return null;
        }
      }));
    }
    boolean successfulTask = false;
    for (Future<Void> future : futures) {
      try {
        future.get();
        if (successfulTask) {
          fail("Two or more futures returned successfully");
        }
        successfulTask = true;
      }
      catch (ExecutionException e) {
        if ("java.lang.IllegalStateException: Unbalanced enter/exit".equals(e.getMessage()) ||
                "java.lang.IllegalArgumentException: Already scheduled".equals(e.getMessage())) {
          // expected
        }
        else {
          throw new RuntimeException(e);
        }
      }
    }
    if (!successfulTask) {
      fail("No futures returned successfully");
    }
    a.exit();
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
    assertEquals(Arrays.asList(expected), timedOut);
  }

  class RecordingAsyncTimeout extends AsyncTimeout {
    @Override protected void timedOut() {
      timedOut.add(this);
    }
  }
}
