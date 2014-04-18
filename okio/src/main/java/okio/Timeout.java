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
import java.util.concurrent.TimeUnit;

/**
 * A policy on how much time to spend on a task before giving up. When a task
 * times out, it is left in an unspecified state and should be abandoned. For
 * example, if reading from a source times out, that source should be closed and
 * the read should be retried later. If writing to a sink times out, the same
 * rules apply: close the sink and retry later.
 *
 * <h3>Timeouts and Deadlines</h3>
 * This class offers two complementary controls to define a timeout policy.
 *
 * <p><strong>Timeouts</strong> specify the maximum time to wait for a single
 * operation to complete. Timeouts are typically used to detect problems like
 * network partitions. For example, if a remote peer doesn't return <i>any</i>
 * data for ten seconds, we may assume that the peer is unavailable.
 *
 * <p><strong>Deadlines</strong> specify the maximum time to spend on a job,
 * composed of one or more operations. Use deadlines to set an upper bound on
 * the time invested on a job. For example, a battery-conscious app may limit
 * how much time it spends preloading content.
 */
public class Timeout {
  /**
   * An empty timeout that neither tracks nor detects timeouts. Use this when
   * timeouts aren't necessary, such as in implementations whose operations
   * do not block.
   */
  public static final Timeout NONE = new Timeout() {
    @Override public Timeout timeout(long timeout, TimeUnit unit) {
      return this;
    }

    @Override public Timeout deadline(long duration, TimeUnit unit) {
      return this;
    }

    @Override public void throwIfReached() throws IOException {
    }
  };

  private long deadlineStart;
  private long deadlineDurationNanos = -1;
  private long timeoutNanos = -1;

  public Timeout() {
  }

  /**
   * Wait at most {@code timeout} time before aborting an operation. Using a
   * per-operation timeout means that as long as forward progress is being made,
   * no sequence of operations will fail.
   */
  public Timeout timeout(long timeout, TimeUnit unit) {
    if (timeout <= 0) throw new IllegalArgumentException("timeout <= 0: " + timeout);
    this.timeoutNanos = unit.toNanos(timeout);
    return this;
  }

  /** Returns the timeout in nanoseconds, or {@code -1} for no timeout. */
  public long getTimeoutNanos() {
    return timeoutNanos;
  }

  /** Returns the time that the deadline was most recently started. */
  public long getDeadlineStart() {
    return deadlineStart;
  }

  /** Returns the deadline duration in nanoseconds, or {@code -1} for no deadline. */
  public long getDeadlineDurationNanos() {
    return deadlineDurationNanos;
  }

  /**
   * Set a deadline of now plus {@code duration} time. All operations must
   * complete before the deadline is reached. Use a deadline to set a maximum
   * bound on the time spent on a sequence of operations.
   */
  public Timeout deadline(long duration, TimeUnit unit) {
    if (duration <= 0) throw new IllegalArgumentException("duration <= 0: " + duration);
    this.deadlineStart = System.nanoTime();
    this.deadlineDurationNanos = unit.toNanos(duration);
    return this;
  }

  /**
   * Clears the timeout and deadline. Operating system timeouts may still apply.
   */
  public Timeout clear() {
    this.deadlineDurationNanos = -1;
    this.timeoutNanos = -1;
    return this;
  }

  /**
   * Throws an {@link IOException} if the deadline has been reached or if the
   * current thread has been interrupted. This method doesn't detect timeouts;
   * that should be implemented to asynchronously abort an in-progress
   * operation.
   */
  public void throwIfReached() throws IOException {
    if (Thread.interrupted()) {
      throw new InterruptedIOException();
    }

    if (deadlineDurationNanos != -1) {
      long now = System.nanoTime();
      long elapsed = now - deadlineStart;
      if (elapsed > deadlineDurationNanos) throw new IOException("deadline reached");
    }
  }
}
