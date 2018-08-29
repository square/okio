/*
 * Copyright (C) 2016 Square, Inc.
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

import java.io.InterruptedIOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class WaitUntilNotifiedTest {
  final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(0);

  @After public void tearDown() {
    executorService.shutdown();
  }

  @Test public synchronized void notified() throws InterruptedIOException {
    Timeout timeout = new Timeout();
    timeout.timeout(5000, TimeUnit.MILLISECONDS);

    double start = now();
    executorService.schedule(new Runnable() {
      @Override public void run() {
        synchronized (WaitUntilNotifiedTest.this) {
          WaitUntilNotifiedTest.this.notify();
        }
      }
    }, 1000, TimeUnit.MILLISECONDS);

    timeout.waitUntilNotified(this);
    assertElapsed(1000.0, start);
  }

  @Test public synchronized void timeout() {
    Timeout timeout = new Timeout();
    timeout.timeout(1000, TimeUnit.MILLISECONDS);
    double start = now();
    try {
      timeout.waitUntilNotified(this);
      fail();
    } catch (InterruptedIOException expected) {
      assertEquals("timeout", expected.getMessage());
    }
    assertElapsed(1000.0, start);
  }

  @Test public synchronized void deadline() {
    Timeout timeout = new Timeout();
    timeout.deadline(1000, TimeUnit.MILLISECONDS);
    double start = now();
    try {
      timeout.waitUntilNotified(this);
      fail();
    } catch (InterruptedIOException expected) {
      assertEquals("timeout", expected.getMessage());
    }
    assertElapsed(1000.0, start);
  }

  @Test public synchronized void deadlineBeforeTimeout() {
    Timeout timeout = new Timeout();
    timeout.timeout(5000, TimeUnit.MILLISECONDS);
    timeout.deadline(1000, TimeUnit.MILLISECONDS);
    double start = now();
    try {
      timeout.waitUntilNotified(this);
      fail();
    } catch (InterruptedIOException expected) {
      assertEquals("timeout", expected.getMessage());
    }
    assertElapsed(1000.0, start);
  }

  @Test public synchronized void timeoutBeforeDeadline() {
    Timeout timeout = new Timeout();
    timeout.timeout(1000, TimeUnit.MILLISECONDS);
    timeout.deadline(5000, TimeUnit.MILLISECONDS);
    double start = now();
    try {
      timeout.waitUntilNotified(this);
      fail();
    } catch (InterruptedIOException expected) {
      assertEquals("timeout", expected.getMessage());
    }
    assertElapsed(1000.0, start);
  }

  @Test public synchronized void deadlineAlreadyReached() {
    Timeout timeout = new Timeout();
    timeout.deadlineNanoTime(System.nanoTime());
    double start = now();
    try {
      timeout.waitUntilNotified(this);
      fail();
    } catch (InterruptedIOException expected) {
      assertEquals("timeout", expected.getMessage());
    }
    assertElapsed(0.0, start);
  }

  @Test public synchronized void threadInterrupted() {
    Timeout timeout = new Timeout();
    double start = now();
    Thread.currentThread().interrupt();
    try {
      timeout.waitUntilNotified(this);
      fail();
    } catch (InterruptedIOException expected) {
      assertEquals("interrupted", expected.getMessage());
      assertTrue(Thread.interrupted());
    }
    assertElapsed(0.0, start);
  }

  @Test public synchronized void threadInterruptedOnThrowIfReached() throws Exception {
    Timeout timeout = new Timeout();
    Thread.currentThread().interrupt();
    try {
      timeout.throwIfReached();
      fail();
    } catch (InterruptedIOException expected) {
      assertEquals("interrupted", expected.getMessage());
      assertTrue(Thread.interrupted());
    }
  }

  /** Returns the nanotime in milliseconds as a double for measuring timeouts. */
  private double now() {
    return System.nanoTime() / 1000000.0d;
  }

  /**
   * Fails the test unless the time from start until now is duration, accepting differences in
   * -50..+450 milliseconds.
   */
  private void assertElapsed(double duration, double start) {
    assertEquals(duration, now() - start - 200d, 250.0);
  }
}
