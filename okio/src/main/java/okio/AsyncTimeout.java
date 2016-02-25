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
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import static okio.Util.checkOffsetAndCount;

/**
 * This timeout uses a background thread to take action exactly when the timeout
 * occurs. Use this to implement timeouts where they aren't supported natively,
 * such as to sockets that are blocked on writing.
 *
 * <p>Subclasses should override {@link #timedOut} to take action when a timeout
 * occurs. This method will be invoked by the shared watchdog thread so it
 * should not do any long-running operations. Otherwise we risk starving other
 * timeouts from being triggered.
 *
 * <p>Use {@link #sink} and {@link #source} to apply this timeout to a stream.
 * The returned value will apply the timeout to each operation on the wrapped
 * stream.
 *
 * <p>Callers should call {@link #enter} before doing work that is subject to
 * timeouts, and {@link #exit} afterwards. The return value of {@link #exit}
 * indicates whether a timeout was triggered. Note that the call to {@link
 * #timedOut} is asynchronous, and may be called after {@link #exit}.
 */
public class AsyncTimeout extends Timeout implements Delayed {
  /**
   * Don't write more than 64 KiB of data at a time, give or take a segment.
   * Otherwise slow connections may suffer timeouts even when they're making
   * (slow) progress. Without this, writing a single 1 MiB buffer may never
   * succeed on a sufficiently slow connection.
   */
  private static final int TIMEOUT_WRITE_SIZE = 64 * 1024;

  /**
   * The watchdog thread processes a queue of pending timeouts, sorted in the order to be triggered.
   */
  private static final Object watchdogLock = new Object();
  private static volatile DelayQueue<AsyncTimeout> queue;

  /** True if this node is currently in the queue. */
  private boolean inQueue;

  /** The next node in the linked list. */
  private AsyncTimeout next;

  /** If scheduled, this is the time that the watchdog should time this out. */
  private long timeoutAt;

  public final void enter() {
    if (inQueue) throw new IllegalStateException("Unbalanced enter/exit");
    long timeoutNanos = timeoutNanos();
    boolean hasDeadline = hasDeadline();
    if (timeoutNanos == 0 && !hasDeadline) {
      return; // No timeout and no deadline? Don't bother with the queue.
    }
    inQueue = true;
    scheduleTimeout(this, timeoutNanos, hasDeadline);
  }

  private void scheduleTimeout(
      AsyncTimeout node, long timeoutNanos, boolean hasDeadline) {
    // Start the watchdog thread and create the head node when the first timeout is scheduled.
    // Double-check lock to initialize the watchdog thread.
    if (queue == null) {
      synchronized (watchdogLock) {
        if (queue == null) {
          queue = new DelayQueue<AsyncTimeout>();
          final Watchdog watchdog = new Watchdog();
          watchdog.setPriority(Thread.MIN_PRIORITY);
          watchdog.start();
        }
      }
    }

    long now = System.nanoTime();
    if (timeoutNanos != 0 && hasDeadline) {
      // Compute the earliest event; either timeout or deadline. Because nanoTime can wrap around,
      // Math.min() is undefined for absolute values, but meaningful for relative ones.
      node.timeoutAt = now + Math.min(timeoutNanos, node.deadlineNanoTime() - now);
    } else if (timeoutNanos != 0) {
      node.timeoutAt = now + timeoutNanos;
    } else if (hasDeadline) {
      node.timeoutAt = node.deadlineNanoTime();
    } else {
      throw new AssertionError();
    }

    // Insert the node in timeout order.
    queue.offer(node);
  }

  /** Returns true if the timeout occurred. */
  public final boolean exit() {
    if (!inQueue) return false;
    inQueue = false;
    return cancelScheduledTimeout(this);
  }

  /** Returns true if the timeout occurred. */
  private boolean cancelScheduledTimeout(AsyncTimeout node) {
    // Remove the node from the linked list.  If the node wasn't found in the queue, it must have
    // timed out!
    return !queue.remove(node);
  }

  /**
   * Invoked by the watchdog thread when the time between calls to {@link
   * #enter()} and {@link #exit()} has exceeded the timeout.
   */
  protected void timedOut() {
  }

  @Override
  public final long getDelay(TimeUnit unit) {
    return unit.convert(timeoutAt - System.nanoTime(), TimeUnit.NANOSECONDS);
  }

  @Override
  public final int compareTo(Delayed that) {
    if (that instanceof AsyncTimeout) {
      return (int) (timeoutAt - ((AsyncTimeout) that).timeoutAt);
    }
    return (int) (getDelay(TimeUnit.NANOSECONDS) - that.getDelay(TimeUnit.NANOSECONDS));
  }

  /**
   * Returns a new sink that delegates to {@code sink}, using this to implement
   * timeouts. This works best if {@link #timedOut} is overridden to interrupt
   * {@code sink}'s current operation.
   */
  public final Sink sink(final Sink sink) {
    return new Sink() {
      @Override public void write(Buffer source, long byteCount) throws IOException {
        checkOffsetAndCount(source.size, 0, byteCount);

        while (byteCount > 0L) {
          // Count how many bytes to write. This loop guarantees we split on a segment boundary.
          long toWrite = 0L;
          for (Segment s = source.head; toWrite < TIMEOUT_WRITE_SIZE; s = s.next) {
            int segmentSize = source.head.limit - source.head.pos;
            toWrite += segmentSize;
            if (toWrite >= byteCount) {
              toWrite = byteCount;
              break;
            }
          }

          // Emit one write. Only this section is subject to the timeout.
          boolean throwOnTimeout = false;
          enter();
          try {
            sink.write(source, toWrite);
            byteCount -= toWrite;
            throwOnTimeout = true;
          } catch (IOException e) {
            throw exit(e);
          } finally {
            exit(throwOnTimeout);
          }
        }
      }

      @Override public void flush() throws IOException {
        boolean throwOnTimeout = false;
        enter();
        try {
          sink.flush();
          throwOnTimeout = true;
        } catch (IOException e) {
          throw exit(e);
        } finally {
          exit(throwOnTimeout);
        }
      }

      @Override public void close() throws IOException {
        boolean throwOnTimeout = false;
        enter();
        try {
          sink.close();
          throwOnTimeout = true;
        } catch (IOException e) {
          throw exit(e);
        } finally {
          exit(throwOnTimeout);
        }
      }

      @Override public Timeout timeout() {
        return AsyncTimeout.this;
      }

      @Override public String toString() {
        return "AsyncTimeout.sink(" + sink + ")";
      }
    };
  }

  /**
   * Returns a new source that delegates to {@code source}, using this to
   * implement timeouts. This works best if {@link #timedOut} is overridden to
   * interrupt {@code sink}'s current operation.
   */
  public final Source source(final Source source) {
    return new Source() {
      @Override public long read(Buffer sink, long byteCount) throws IOException {
        boolean throwOnTimeout = false;
        enter();
        try {
          long result = source.read(sink, byteCount);
          throwOnTimeout = true;
          return result;
        } catch (IOException e) {
          throw exit(e);
        } finally {
          exit(throwOnTimeout);
        }
      }

      @Override public void close() throws IOException {
        boolean throwOnTimeout = false;
        try {
          source.close();
          throwOnTimeout = true;
        } catch (IOException e) {
          throw exit(e);
        } finally {
          exit(throwOnTimeout);
        }
      }

      @Override public Timeout timeout() {
        return AsyncTimeout.this;
      }

      @Override public String toString() {
        return "AsyncTimeout.source(" + source + ")";
      }
    };
  }

  /**
   * Throws an IOException if {@code throwOnTimeout} is {@code true} and a
   * timeout occurred. See {@link #newTimeoutException(java.io.IOException)}
   * for the type of exception thrown.
   */
  final void exit(boolean throwOnTimeout) throws IOException {
    boolean timedOut = exit();
    if (timedOut && throwOnTimeout) throw newTimeoutException(null);
  }

  /**
   * Returns either {@code cause} or an IOException that's caused by
   * {@code cause} if a timeout occurred. See
   * {@link #newTimeoutException(java.io.IOException)} for the type of
   * exception returned.
   */
  final IOException exit(IOException cause) throws IOException {
    if (!exit()) return cause;
    return newTimeoutException(cause);
  }

  /**
   * Returns an {@link IOException} to represent a timeout. By default this method returns
   * {@link java.io.InterruptedIOException}. If {@code cause} is non-null it is set as the cause of
   * the returned exception.
   */
  protected IOException newTimeoutException(IOException cause) {
    InterruptedIOException e = new InterruptedIOException("timeout");
    if (cause != null) {
      e.initCause(cause);
    }
    return e;
  }

  private static final class Watchdog extends Thread {
    public Watchdog() {
      super("Okio Watchdog");
      setDaemon(true);
    }

    public void run() {
      while (true) {
        try {
          final AsyncTimeout timedOut = queue.take();
          // Close the timed out node.
          timedOut.timedOut();
        } catch (InterruptedException ignored) {
        }
        Thread.yield();
      }
    }
  }
}
