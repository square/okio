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
package okio

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toTimeUnit

actual open class Timeout {
  /**
   * True if `deadlineNanoTime` is defined. There is no equivalent to null or 0 for
   * [System.nanoTime].
   */
  private var hasDeadline = false
  private var deadlineNanoTime = 0L
  private var timeoutNanos = 0L

  /**
   * A sentinel that is updated to a new object on each call to [cancel]. Sample this property
   * before and after an operation to test if the timeout was canceled during the operation.
   */
  @Volatile private var cancelMark: Any? = null

  /**
   * Wait at most `timeout` time before aborting an operation. Using a per-operation timeout means
   * that as long as forward progress is being made, no sequence of operations will fail.
   *
   * If `timeout == 0`, operations will run indefinitely. (Operating system timeouts may still
   * apply.)
   */
  open fun timeout(timeout: Long, unit: TimeUnit): Timeout {
    require(timeout >= 0) { "timeout < 0: $timeout" }
    timeoutNanos = unit.toNanos(timeout)
    return this
  }

  /** Returns the timeout in nanoseconds, or `0` for no timeout. */
  open fun timeoutNanos(): Long = timeoutNanos

  /** Returns true if a deadline is enabled. */
  open fun hasDeadline(): Boolean = hasDeadline

  /**
   * Returns the [nano time][System.nanoTime] when the deadline will be reached.
   *
   * @throws IllegalStateException if no deadline is set.
   */
  open fun deadlineNanoTime(): Long {
    check(hasDeadline) { "No deadline" }
    return deadlineNanoTime
  }

  /**
   * Sets the [nano time][System.nanoTime] when the deadline will be reached. All operations must
   * complete before this time. Use a deadline to set a maximum bound on the time spent on a
   * sequence of operations.
   */
  open fun deadlineNanoTime(deadlineNanoTime: Long): Timeout {
    this.hasDeadline = true
    this.deadlineNanoTime = deadlineNanoTime
    return this
  }

  /** Set a deadline of now plus `duration` time.  */
  fun deadline(duration: Long, unit: TimeUnit): Timeout {
    require(duration > 0) { "duration <= 0: $duration" }
    return deadlineNanoTime(System.nanoTime() + unit.toNanos(duration))
  }

  /** Clears the timeout. Operating system timeouts may still apply. */
  open fun clearTimeout(): Timeout {
    timeoutNanos = 0
    return this
  }

  /** Clears the deadline. */
  open fun clearDeadline(): Timeout {
    hasDeadline = false
    return this
  }

  /**
   * Throws an [InterruptedIOException] if the deadline has been reached or if the current thread
   * has been interrupted. This method doesn't detect timeouts; that should be implemented to
   * asynchronously abort an in-progress operation.
   */
  @Throws(IOException::class)
  open fun throwIfReached() {
    if (Thread.currentThread().isInterrupted) {
      // If the current thread has been interrupted.
      throw InterruptedIOException("interrupted")
    }

    if (hasDeadline && deadlineNanoTime - System.nanoTime() <= 0) {
      throw InterruptedIOException("deadline reached")
    }
  }

  /**
   * Prevent all current applications of this timeout from firing. Use this when a time-limited
   * operation should no longer be time-limited because the nature of the operation has changed.
   *
   * This function does not mutate the [deadlineNanoTime] or [timeoutNanos] properties of this
   * timeout. It only applies to active operations that are limited by this timeout, and applies by
   * allowing those operations to run indefinitely.
   *
   * Subclasses that override this method must call `super.cancel()`.
   */
  open fun cancel() {
    cancelMark = Any()
  }

  /**
   * Waits on `monitor` until it is signaled. Throws [InterruptedIOException] if either the thread
   * is interrupted or if this timeout elapses before `monitor` is signaled.
   * The caller must hold the lock that monitor is bound to.
   *
   * Here's a sample class that uses `awaitSignal()` to await a specific state. Note that the
   * call is made within a loop to avoid unnecessary waiting and to mitigate spurious notifications.
   *
   * ```java
   * class Dice {
   *   Random random = new Random();
   *   int latestTotal;
   *
   *   ReentrantLock lock = new ReentrantLock();
   *   Condition condition = lock.newCondition();
   *
   *   public void roll() {
   *     lock.withLock {
   *       latestTotal = 2 + random.nextInt(6) + random.nextInt(6);
   *       System.out.println("Rolled " + latestTotal);
   *       condition.signalAll();
   *     }
   *   }
   *
   *   public void rollAtFixedRate(int period, TimeUnit timeUnit) {
   *     Executors.newScheduledThreadPool(0).scheduleAtFixedRate(new Runnable() {
   *       public void run() {
   *         roll();
   *       }
   *     }, 0, period, timeUnit);
   *   }
   *
   *   public void awaitTotal(Timeout timeout, int total)
   *       throws InterruptedIOException {
   *     lock.withLock {
   *       while (latestTotal != total) {
   *         timeout.awaitSignal(this);
   *       }
   *     }
   *   }
   * }
   * ```
   */
  @Throws(InterruptedIOException::class)
  open fun awaitSignal(condition: Condition) {
    try {
      val hasDeadline = hasDeadline()
      val timeoutNanos = timeoutNanos()

      if (!hasDeadline && timeoutNanos == 0L) {
        condition.await() // There is no timeout: wait forever.
        return
      }

      // Compute how long we'll wait.
      val waitNanos = if (hasDeadline && timeoutNanos != 0L) {
        val deadlineNanos = deadlineNanoTime() - System.nanoTime()
        minOf(timeoutNanos, deadlineNanos)
      } else if (hasDeadline) {
        deadlineNanoTime() - System.nanoTime()
      } else {
        timeoutNanos
      }

      if (waitNanos <= 0) throw InterruptedIOException("timeout")

      val cancelMarkBefore = cancelMark

      // Attempt to wait that long. This will return early if the monitor is notified.
      val nanosRemaining = condition.awaitNanos(waitNanos)

      // If there's time remaining, we probably got the call we were waiting for.
      if (nanosRemaining > 0) return

      // Return without throwing if this timeout was canceled while we were waiting. Note that this
      // return is a 'spurious wakeup' because Condition.signal() was not called.
      if (cancelMark !== cancelMarkBefore) return

      throw InterruptedIOException("timeout")
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt() // Retain interrupted status.
      throw InterruptedIOException("interrupted")
    }
  }

  /**
   * Waits on `monitor` until it is notified. Throws [InterruptedIOException] if either the thread
   * is interrupted or if this timeout elapses before `monitor` is notified. The caller must be
   * synchronized on `monitor`.
   *
   * Here's a sample class that uses `waitUntilNotified()` to await a specific state. Note that the
   * call is made within a loop to avoid unnecessary waiting and to mitigate spurious notifications.
   *
   * ```java
   * class Dice {
   *   Random random = new Random();
   *   int latestTotal;
   *
   *   public synchronized void roll() {
   *     latestTotal = 2 + random.nextInt(6) + random.nextInt(6);
   *     System.out.println("Rolled " + latestTotal);
   *     notifyAll();
   *   }
   *
   *   public void rollAtFixedRate(int period, TimeUnit timeUnit) {
   *     Executors.newScheduledThreadPool(0).scheduleAtFixedRate(new Runnable() {
   *       public void run() {
   *         roll();
   *       }
   *     }, 0, period, timeUnit);
   *   }
   *
   *   public synchronized void awaitTotal(Timeout timeout, int total)
   *       throws InterruptedIOException {
   *     while (latestTotal != total) {
   *       timeout.waitUntilNotified(this);
   *     }
   *   }
   * }
   * ```
   */
  @Throws(InterruptedIOException::class)
  open fun waitUntilNotified(monitor: Any) {
    try {
      val hasDeadline = hasDeadline()
      val timeoutNanos = timeoutNanos()

      if (!hasDeadline && timeoutNanos == 0L) {
        (monitor as Object).wait() // There is no timeout: wait forever.
        return
      }

      // Compute how long we'll wait.
      val start = System.nanoTime()
      val waitNanos = if (hasDeadline && timeoutNanos != 0L) {
        val deadlineNanos = deadlineNanoTime() - start
        minOf(timeoutNanos, deadlineNanos)
      } else if (hasDeadline) {
        deadlineNanoTime() - start
      } else {
        timeoutNanos
      }

      if (waitNanos <= 0) throw InterruptedIOException("timeout")

      val cancelMarkBefore = cancelMark

      // Attempt to wait that long. This will return early if the monitor is notified.
      val waitMillis = waitNanos / 1000000L
      (monitor as Object).wait(waitMillis, (waitNanos - waitMillis * 1000000L).toInt())
      val elapsedNanos = System.nanoTime() - start

      // If there's time remaining, we probably got the call we were waiting for.
      if (elapsedNanos < waitNanos) return

      // Return without throwing if this timeout was canceled while we were waiting. Note that this
      // return is a 'spurious wakeup' because Object.notify() was not called.
      if (cancelMark !== cancelMarkBefore) return

      throw InterruptedIOException("timeout")
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt() // Retain interrupted status.
      throw InterruptedIOException("interrupted")
    }
  }

  /**
   * Applies the minimum intersection between this timeout and `other`, run `block`, then finally
   * rollback this timeout's values.
   */
  inline fun <T> intersectWith(other: Timeout, block: () -> T): T {
    val originalTimeout = this.timeoutNanos()
    this.timeout(minTimeout(other.timeoutNanos(), this.timeoutNanos()), TimeUnit.NANOSECONDS)

    if (this.hasDeadline()) {
      val originalDeadline = this.deadlineNanoTime()
      if (other.hasDeadline()) {
        this.deadlineNanoTime(Math.min(this.deadlineNanoTime(), other.deadlineNanoTime()))
      }
      try {
        return block()
      } finally {
        this.timeout(originalTimeout, TimeUnit.NANOSECONDS)
        if (other.hasDeadline()) {
          this.deadlineNanoTime(originalDeadline)
        }
      }
    } else {
      if (other.hasDeadline()) {
        this.deadlineNanoTime(other.deadlineNanoTime())
      }
      try {
        return block()
      } finally {
        this.timeout(originalTimeout, TimeUnit.NANOSECONDS)
        if (other.hasDeadline()) {
          this.clearDeadline()
        }
      }
    }
  }

  actual companion object {
    @JvmField actual val NONE: Timeout = object : Timeout() {
      override fun timeout(timeout: Long, unit: TimeUnit): Timeout = this

      override fun deadlineNanoTime(deadlineNanoTime: Long): Timeout = this

      override fun throwIfReached() {}
    }

    fun Timeout.timeout(timeout: Long, unit: DurationUnit): Timeout {
      return timeout(timeout, unit.toTimeUnit())
    }

    fun Timeout.timeout(duration: Duration): Timeout {
      return timeout(duration.inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }

    fun minTimeout(aNanos: Long, bNanos: Long) = when {
      aNanos == 0L -> bNanos
      bNanos == 0L -> aNanos
      aNanos < bNanos -> aNanos
      else -> bNanos
    }
  }
}
