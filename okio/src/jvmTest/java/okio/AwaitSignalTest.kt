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

import okio.TestUtil.assumeNotWindows
import org.junit.After
import org.junit.Assert
import org.junit.Test
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock

class AwaitSignalTest {
  val executorService = TestingExecutors.newScheduledExecutorService(0)

  val lock: ReentrantLock = ReentrantLock()
  val condition: Condition = lock.newCondition()

  @After
  fun tearDown() {
    executorService.shutdown()
  }

  @Test
  fun notified() = lock.withLock {
    val timeout = Timeout()
    timeout.timeout(5000, TimeUnit.MILLISECONDS)
    val start = now()
    executorService.schedule(
      { lock.withLock { condition.signal() } },
      1000,
      TimeUnit.MILLISECONDS
    )
    timeout.awaitSignal(condition)
    assertElapsed(1000.0, start)
  }

  @Test
  fun timeout() = lock.withLock {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.awaitSignal(condition)
      Assert.fail()
    } catch (expected: InterruptedIOException) {
      Assert.assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun deadline() = lock.withLock {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.deadline(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.awaitSignal(condition)
      Assert.fail()
    } catch (expected: InterruptedIOException) {
      Assert.assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun deadlineBeforeTimeout() = lock.withLock {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.timeout(5000, TimeUnit.MILLISECONDS)
    timeout.deadline(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.awaitSignal(condition)
      Assert.fail()
    } catch (expected: InterruptedIOException) {
      Assert.assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun timeoutBeforeDeadline() = lock.withLock {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    timeout.deadline(5000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.awaitSignal(condition)
      Assert.fail()
    } catch (expected: InterruptedIOException) {
      Assert.assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  fun deadlineAlreadyReached() = lock.withLock {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.deadlineNanoTime(System.nanoTime())
    val start = now()
    try {
      timeout.awaitSignal(condition)
      Assert.fail()
    } catch (expected: InterruptedIOException) {
      Assert.assertEquals("timeout", expected.message)
    }
    assertElapsed(0.0, start)
  }

  @Test
  fun threadInterrupted() = lock.withLock {
    assumeNotWindows()
    val timeout = Timeout()
    val start = now()
    Thread.currentThread().interrupt()
    try {
      timeout.awaitSignal(condition)
      Assert.fail()
    } catch (expected: InterruptedIOException) {
      Assert.assertEquals("interrupted", expected.message)
      Assert.assertTrue(Thread.interrupted())
    }
    assertElapsed(0.0, start)
  }

  @Test
  fun threadInterruptedOnThrowIfReached() = lock.withLock {
    assumeNotWindows()
    val timeout = Timeout()
    Thread.currentThread().interrupt()
    try {
      timeout.throwIfReached()
      Assert.fail()
    } catch (expected: InterruptedIOException) {
      Assert.assertEquals("interrupted", expected.message)
      Assert.assertTrue(Thread.interrupted())
    }
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
    Assert.assertEquals(duration, now() - start - 200.0, 250.0)
  }
}
