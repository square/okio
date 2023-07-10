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
package okio

import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import okio.TestUtil.assumeNotWindows
import okio.TestingExecutors.newScheduledExecutorService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WaitUntilNotifiedTest {
  private val executorService = newScheduledExecutorService(0)

  @After
  fun tearDown() {
    executorService.shutdown()
  }

  @Test
  @Synchronized
  fun notified() {
    val timeout = Timeout()
    timeout.timeout(5000, TimeUnit.MILLISECONDS)
    val start = now()
    executorService.schedule(
      {
        synchronized(this@WaitUntilNotifiedTest) {
          (this as Object).notify()
        }
      },
      1000,
      TimeUnit.MILLISECONDS,
    )
    timeout.waitUntilNotified(this)
    assertElapsed(1000.0, start)
  }

  @Test
  @Synchronized
  fun timeout() {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.waitUntilNotified(this)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  @Synchronized
  fun deadline() {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.deadline(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.waitUntilNotified(this)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  @Synchronized
  fun deadlineBeforeTimeout() {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.timeout(5000, TimeUnit.MILLISECONDS)
    timeout.deadline(1000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.waitUntilNotified(this)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  @Synchronized
  fun timeoutBeforeDeadline() {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.timeout(1000, TimeUnit.MILLISECONDS)
    timeout.deadline(5000, TimeUnit.MILLISECONDS)
    val start = now()
    try {
      timeout.waitUntilNotified(this)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(1000.0, start)
  }

  @Test
  @Synchronized
  fun deadlineAlreadyReached() {
    assumeNotWindows()
    val timeout = Timeout()
    timeout.deadlineNanoTime(System.nanoTime())
    val start = now()
    try {
      timeout.waitUntilNotified(this)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("timeout", expected.message)
    }
    assertElapsed(0.0, start)
  }

  @Test
  @Synchronized
  fun threadInterrupted() {
    assumeNotWindows()
    val timeout = Timeout()
    val start = now()
    Thread.currentThread().interrupt()
    try {
      timeout.waitUntilNotified(this)
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("interrupted", expected.message)
      assertTrue(Thread.interrupted())
    }
    assertElapsed(0.0, start)
  }

  @Test
  @Synchronized
  fun threadInterruptedOnThrowIfReached() {
    assumeNotWindows()
    val timeout = Timeout()
    Thread.currentThread().interrupt()
    try {
      timeout.throwIfReached()
      fail()
    } catch (expected: InterruptedIOException) {
      assertEquals("interrupted", expected.message)
      assertTrue(Thread.interrupted())
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
    assertEquals(duration, now() - start - 200.0, 250.0)
  }
}
