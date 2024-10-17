/*
 * Copyright (C) 2023 Block Inc.
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

import app.cash.burst.Burst
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import okio.TestUtil.assumeNotWindows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@Burst
class AwaitSignalTest(
  factory: TimeoutFactory,
) {
  private val timeout = factory.newTimeout()
  val executorService = TestingExecutors.newScheduledExecutorService(0)

  val lock: ReentrantLock = ReentrantLock()
  val condition: Condition = lock.newCondition()

  @After
  fun tearDown() {
    executorService.shutdown()
  }

  @Test
  fun signaled() = lock.withLock {
    timeout.timeout(5000, TimeUnit.MILLISECONDS)
    val start = now()
    executorService.schedule(
      { lock.withLock { condition.signal() } },
      1000,
      TimeUnit.MILLISECONDS,
    )
    timeout.awaitSignal(condition)
    assertElapsed(1000.0, start)
  }

  @Test
  fun timeout() = lock.withLock {
    assumeNotWindows()
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.awaitSignal(condition)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun deadline() = lock.withLock {
    assumeNotWindows()
    timeout.deadline(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.awaitSignal(condition)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun deadlineBeforeTimeout() = lock.withLock {
    assumeNotWindows()
    timeout.timeout(5000, TimeUnit.MILLISECONDS)
    timeout.deadline(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.awaitSignal(condition)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun timeoutBeforeDeadline() = lock.withLock {
    assumeNotWindows()
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    timeout.deadline(5000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.awaitSignal(condition)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun deadlineAlreadyReached() = lock.withLock {
    assumeNotWindows()
    timeout.deadlineNanoTime(System.nanoTime())
    val start = now()
    try {
      timeout.awaitSignal(condition)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(0.0, start)
  }

  @Test
  fun threadInterrupted() = lock.withLock {
    assumeNotWindows()
    val start = now()
    Thread.currentThread().interrupt()
    try {
      timeout.awaitSignal(condition)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("interrupted", expected.message)
      assertTrue(Thread.interrupted())
    }
    assertElapsed(0.0, start)
  }

  @Test
  fun threadInterruptedOnThrowIfReached() = lock.withLock {
    assumeNotWindows()
    Thread.currentThread().interrupt()
    try {
      timeout.throwIfReached()
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("interrupted", expected.message)
      assertTrue(Thread.interrupted())
    }
  }

  @Test
  fun cancelBeforeWaitDoesNothing() = lock.withLock {
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    timeout.cancel()
    val start = now()
    try {
      timeout.awaitSignal(condition)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun canceledTimeoutDoesNotThrowWhenNotNotifiedOnTime() = lock.withLock {
    assumeNotWindows()
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    timeout.cancelLater(500)

    val start = now()
    timeout.awaitSignal(condition) // Returns early but doesn't throw.
    assertElapsed(1000.0, start)
  }

  @Test
  @Synchronized
  fun multipleCancelsAreIdempotent() = lock.withLock {
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    timeout.cancelLater(250)
    timeout.cancelLater(500)
    timeout.cancelLater(750)

    val start = now()
    timeout.awaitSignal(condition) // Returns early but doesn't throw.
    assertElapsed(1000.0, start)
  }

  /** Returns the nanotime in milliseconds as a double for measuring timeouts.  */
  private fun now(): Double {
    return System.nanoTime() / 1000000.0
  }

  /**
   * Fails the test unless the time from start until now is duration, accepting differences in
   * -50..+450 milliseconds.
   */
  private fun assertElapsed(duration: Double, start: Double) {
    assertEquals(duration, now() - start - 200.0, 250.0)
  }

  private fun Timeout.cancelLater(delay: Long) {
    executorService.schedule(
      { cancel() },
      delay,
      TimeUnit.MILLISECONDS,
    )
  }
}
