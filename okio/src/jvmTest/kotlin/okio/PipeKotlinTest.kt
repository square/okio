/*
 * Copyright (C) 2018 Square, Inc.
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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import org.junit.rules.Timeout as JUnitTimeout

class PipeKotlinTest {
  @JvmField @Rule val timeout = JUnitTimeout(5, TimeUnit.SECONDS)

  private val executorService = Executors.newScheduledThreadPool(1)

  @After @Throws(Exception::class)
  fun tearDown() {
    executorService.shutdown()
  }

  @Test fun pipe() {
    val pipe = Pipe(6)
    pipe.sink.write(Buffer().writeUtf8("abc"), 3L)

    val readBuffer = Buffer()
    assertEquals(3L, pipe.source.read(readBuffer, 6L))
    assertEquals("abc", readBuffer.readUtf8())

    pipe.sink.close()
    assertEquals(-1L, pipe.source.read(readBuffer, 6L))

    pipe.source.close()
  }

  @Test fun fold() {
    val pipe = Pipe(128)

    val pipeSink = pipe.sink.buffer()
    pipeSink.writeUtf8("hello")
    pipeSink.emit()

    val pipeSource = pipe.source.buffer()
    assertEquals("hello", pipeSource.readUtf8(5))

    val foldedSinkBuffer = Buffer()
    var foldedSinkClosed = false
    val foldedSink = object : ForwardingSink(foldedSinkBuffer) {
      override fun close() {
        foldedSinkClosed = true
        super.close()
      }
    }
    pipe.fold(foldedSink)

    pipeSink.writeUtf8("world")
    pipeSink.emit()
    assertEquals("world", foldedSinkBuffer.readUtf8(5))

    assertFailsWith<IllegalStateException> {
      pipeSource.readUtf8()
    }

    pipeSink.close()
    assertTrue(foldedSinkClosed)
  }

  @Test fun foldWritesPipeContentsToSink() {
    val pipe = Pipe(128)

    val pipeSink = pipe.sink.buffer()
    pipeSink.writeUtf8("hello")
    pipeSink.emit()

    val foldSink = Buffer()
    pipe.fold(foldSink)

    assertEquals("hello", foldSink.readUtf8(5))
  }

  @Test fun foldUnblocksBlockedWrite() {
    val pipe = Pipe(4)
    val foldSink = Buffer()

    val latch = CountDownLatch(1)
    executorService.schedule({
      pipe.fold(foldSink)
      latch.countDown()
    }, 500, TimeUnit.MILLISECONDS)

    val sink = pipe.sink.buffer()
    sink.writeUtf8("abcdefgh") // Blocks writing 8 bytes to a 4 byte pipe.
    sink.close()

    latch.await()
    assertEquals("abcdefgh", foldSink.readUtf8())
  }

  @Test fun accessSourceAfterFold() {
    val pipe = Pipe(100L)
    pipe.fold(Buffer())
    assertFailsWith<IllegalStateException> {
      pipe.source.read(Buffer(), 1L)
    }
  }

  @Test fun honorsPipeSinkTimeoutOnWritingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutWritingSink()

    underlying.timeout.timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)
    pipe.sink.timeout().timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(smallerTimeoutNanos) {
      pipe.sink.write(Buffer().writeUtf8("abc"), 3)
    }
    assertEquals(biggerTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsUnderlyingTimeoutOnWritingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutWritingSink()

    underlying.timeout.timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)
    pipe.sink.timeout().timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(smallerTimeoutNanos) {
      pipe.sink.write(Buffer().writeUtf8("abc"), 3)
    }
    assertEquals(smallerTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsPipeSinkTimeoutOnFlushingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutFlushingSink()

    underlying.timeout.timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)
    pipe.sink.timeout().timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(smallerTimeoutNanos) {
      pipe.sink.flush()
    }
    assertEquals(biggerTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsUnderlyingTimeoutOnFlushingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutFlushingSink()

    underlying.timeout.timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)
    pipe.sink.timeout().timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(smallerTimeoutNanos) {
      pipe.sink.flush()
    }
    assertEquals(smallerTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsPipeSinkTimeoutOnClosingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutClosingSink()

    underlying.timeout.timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)
    pipe.sink.timeout().timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(smallerTimeoutNanos) {
      pipe.sink.close()
    }
    assertEquals(biggerTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsUnderlyingTimeoutOnClosingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutClosingSink()

    underlying.timeout.timeout(smallerTimeoutNanos, TimeUnit.NANOSECONDS)
    pipe.sink.timeout().timeout(biggerTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(smallerTimeoutNanos) {
      pipe.sink.close()
    }
    assertEquals(smallerTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsPipeSinkTimeoutOnWritingWhenUnderlyingSinkTimeoutIsZero() {
    val pipeSinkTimeoutNanos = smallerTimeoutNanos

    val pipe = Pipe(4)
    val underlying = TimeoutWritingSink()

    pipe.sink.timeout().timeout(pipeSinkTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(pipeSinkTimeoutNanos) {
      pipe.sink.write(Buffer().writeUtf8("abc"), 3)
    }
    assertEquals(0L, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsUnderlyingSinkTimeoutOnWritingWhenPipeSinkTimeoutIsZero() {
    val underlyingSinkTimeoutNanos = smallerTimeoutNanos

    val pipe = Pipe(4)
    val underlying = TimeoutWritingSink()

    underlying.timeout().timeout(underlyingSinkTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(underlyingSinkTimeoutNanos) {
      pipe.sink.write(Buffer().writeUtf8("abc"), 3)
    }
    assertEquals(underlyingSinkTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsPipeSinkTimeoutOnFlushingWhenUnderlyingSinkTimeoutIsZero() {
    val pipeSinkTimeoutNanos = smallerTimeoutNanos

    val pipe = Pipe(4)
    val underlying = TimeoutFlushingSink()

    pipe.sink.timeout().timeout(pipeSinkTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(pipeSinkTimeoutNanos) {
      pipe.sink.flush()
    }
    assertEquals(0L, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsUnderlyingSinkTimeoutOnFlushingWhenPipeSinkTimeoutIsZero() {
    val underlyingSinkTimeoutNanos = smallerTimeoutNanos

    val pipe = Pipe(4)
    val underlying = TimeoutFlushingSink()

    underlying.timeout().timeout(underlyingSinkTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(underlyingSinkTimeoutNanos) {
      pipe.sink.flush()
    }
    assertEquals(underlyingSinkTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsPipeSinkTimeoutOnClosingWhenUnderlyingSinkTimeoutIsZero() {
    val pipeSinkTimeoutNanos = smallerTimeoutNanos

    val pipe = Pipe(4)
    val underlying = TimeoutClosingSink()

    pipe.sink.timeout().timeout(pipeSinkTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(pipeSinkTimeoutNanos) {
      pipe.sink.close()
    }
    assertEquals(0L, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsUnderlyingSinkTimeoutOnClosingWhenPipeSinkTimeoutIsZero() {
    val underlyingSinkTimeoutNanos = smallerTimeoutNanos

    val pipe = Pipe(4)
    val underlying = TimeoutClosingSink()

    underlying.timeout().timeout(underlyingSinkTimeoutNanos, TimeUnit.NANOSECONDS)

    pipe.fold(underlying)

    assertDuration(underlyingSinkTimeoutNanos) {
      pipe.sink.close()
    }
    assertEquals(underlyingSinkTimeoutNanos, underlying.timeout().timeoutNanos())
  }

  @Test fun honorsPipeSinkDeadlineOnWritingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutWritingSink()

    val underlyingOriginalDeadline = System.nanoTime() + biggerDeadlineNanos
    underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + smallerDeadlineNanos)

    pipe.fold(underlying)

    assertDuration(smallerDeadlineNanos) {
      pipe.sink.write(Buffer().writeUtf8("abc"), 3)
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun honorsPipeSinkDeadlineOnWritingWhenUnderlyingSinkHasNoDeadline() {
    val deadlineNanos = smallerDeadlineNanos

    val pipe = Pipe(4)
    val underlying = TimeoutWritingSink()

    underlying.timeout.clearDeadline()
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + deadlineNanos)

    pipe.fold(underlying)

    assertDuration(deadlineNanos) {
      pipe.sink.write(Buffer().writeUtf8("abc"), 3)
    }
    assertFalse(underlying.timeout().hasDeadline())
  }

  @Test fun honorsUnderlyingSinkDeadlineOnWritingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutWritingSink()

    val underlyingOriginalDeadline = System.nanoTime() + smallerDeadlineNanos
    underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + biggerDeadlineNanos)

    pipe.fold(underlying)

    assertDuration(smallerDeadlineNanos) {
      pipe.sink.write(Buffer().writeUtf8("abc"), 3)
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun honorsUnderlyingSinkDeadlineOnWritingWhenPipeSinkHasNoDeadline() {
    val deadlineNanos = smallerDeadlineNanos

    val pipe = Pipe(4)
    val underlying = TimeoutWritingSink()

    val underlyingOriginalDeadline = System.nanoTime() + deadlineNanos
    underlying.timeout().deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().clearDeadline()

    pipe.fold(underlying)

    assertDuration(deadlineNanos) {
      pipe.sink.write(Buffer().writeUtf8("abc"), 3)
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun honorsPipeSinkDeadlineOnFlushingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutFlushingSink()

    val underlyingOriginalDeadline = System.nanoTime() + biggerDeadlineNanos
    underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + smallerDeadlineNanos)

    pipe.fold(underlying)

    assertDuration(smallerDeadlineNanos) {
      pipe.sink.flush()
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun honorsPipeSinkDeadlineOnFlushingWhenUnderlyingSinkHasNoDeadline() {
    val deadlineNanos = smallerDeadlineNanos

    val pipe = Pipe(4)
    val underlying = TimeoutFlushingSink()

    underlying.timeout.clearDeadline()
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + deadlineNanos)

    pipe.fold(underlying)

    assertDuration(deadlineNanos) {
      pipe.sink.flush()
    }
    assertFalse(underlying.timeout().hasDeadline())
  }

  @Test fun honorsUnderlyingSinkDeadlineOnFlushingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutFlushingSink()

    val underlyingOriginalDeadline = System.nanoTime() + smallerDeadlineNanos
    underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + biggerDeadlineNanos)

    pipe.fold(underlying)

    assertDuration(smallerDeadlineNanos) {
      pipe.sink.flush()
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun honorsUnderlyingSinkDeadlineOnFlushingWhenPipeSinkHasNoDeadline() {
    val deadlineNanos = smallerDeadlineNanos

    val pipe = Pipe(4)
    val underlying = TimeoutFlushingSink()

    val underlyingOriginalDeadline = System.nanoTime() + deadlineNanos
    underlying.timeout().deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().clearDeadline()

    pipe.fold(underlying)

    assertDuration(deadlineNanos) {
      pipe.sink.flush()
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun honorsPipeSinkDeadlineOnClosingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutClosingSink()

    val underlyingOriginalDeadline = System.nanoTime() + biggerDeadlineNanos
    underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + smallerDeadlineNanos)

    pipe.fold(underlying)

    assertDuration(smallerDeadlineNanos) {
      pipe.sink.close()
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun honorsPipeSinkDeadlineOnClosingWhenUnderlyingSinkHasNoDeadline() {
    val deadlineNanos = smallerDeadlineNanos

    val pipe = Pipe(4)
    val underlying = TimeoutClosingSink()

    underlying.timeout.clearDeadline()
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + deadlineNanos)

    pipe.fold(underlying)

    assertDuration(deadlineNanos) {
      pipe.sink.close()
    }
    assertFalse(underlying.timeout().hasDeadline())
  }

  @Test fun honorsUnderlyingSinkDeadlineOnClosingWhenItIsSmaller() {
    val pipe = Pipe(4)
    val underlying = TimeoutClosingSink()

    val underlyingOriginalDeadline = System.nanoTime() + smallerDeadlineNanos
    underlying.timeout.deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().deadlineNanoTime(System.nanoTime() + biggerDeadlineNanos)

    pipe.fold(underlying)

    assertDuration(smallerDeadlineNanos) {
      pipe.sink.close()
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun honorsUnderlyingSinkDeadlineOnClosingWhenPipeSinkHasNoDeadline() {
    val deadlineNanos = smallerDeadlineNanos

    val pipe = Pipe(4)
    val underlying = TimeoutClosingSink()

    val underlyingOriginalDeadline = System.nanoTime() + deadlineNanos
    underlying.timeout().deadlineNanoTime(underlyingOriginalDeadline)
    pipe.sink.timeout().clearDeadline()

    pipe.fold(underlying)

    assertDuration(deadlineNanos) {
      pipe.sink.close()
    }
    assertEquals(underlyingOriginalDeadline, underlying.timeout().deadlineNanoTime())
  }

  @Test fun foldingTwiceThrows() {
    val pipe = Pipe(128)
    pipe.fold(Buffer())
    assertFailsWith<IllegalStateException> {
      pipe.fold(Buffer())
    }
  }

  @Test fun sinkWriteThrowsIOExceptionUnblockBlockedWriter() {
    val pipe = Pipe(4)

    val foldFuture = executorService.schedule({
      val foldFailure = assertFailsWith<IOException> {
        pipe.fold(object : ForwardingSink(blackholeSink()) {
          override fun write(source: Buffer, byteCount: Long) {
            throw IOException("boom")
          }
        })
      }
      assertEquals("boom", foldFailure.message)
    }, 500, TimeUnit.MILLISECONDS)

    val writeFailure = assertFailsWith<IOException> {
      val pipeSink = pipe.sink.buffer()
      pipeSink.writeUtf8("abcdefghij")
      pipeSink.emit() // Block writing 10 bytes to a 4 byte pipe.
    }
    assertEquals("source is closed", writeFailure.message)

    foldFuture.get() // Confirm no unexpected exceptions.
  }

  @Test fun foldHoldsNoLocksWhenForwardingWrites() {
    val pipe = Pipe(4)

    val pipeSink = pipe.sink.buffer()
    pipeSink.writeUtf8("abcd")
    pipeSink.emit()

    pipe.fold(object : ForwardingSink(blackholeSink()) {
      override fun write(source: Buffer, byteCount: Long) {
        assertFalse(Thread.holdsLock(pipe.buffer))
      }
    })
  }

  /**
   * Flushing the pipe wasn't causing the sink to be flushed when it was later folded. This was
   * causing problems because the folded data was stalled.
   */
  @Test fun foldFlushesWhenThereIsFoldedData() {
    val pipe = Pipe(128)
    val pipeSink = pipe.sink.buffer()
    pipeSink.writeUtf8("hello")
    pipeSink.emit()

    val ultimateSink = Buffer()
    val unnecessaryWrapper = (ultimateSink as Sink).buffer()

    pipe.fold(unnecessaryWrapper)

    // Data should not have been flushed through the wrapper to the ultimate sink.
    assertEquals("hello", ultimateSink.readUtf8())
  }

  @Test fun foldDoesNotFlushWhenThereIsNoFoldedData() {
    val pipe = Pipe(128)

    val ultimateSink = Buffer()
    val unnecessaryWrapper = (ultimateSink as Sink).buffer()
    unnecessaryWrapper.writeUtf8("hello")

    pipe.fold(unnecessaryWrapper)

    // Data should not have been flushed through the wrapper to the ultimate sink.
    assertEquals("", ultimateSink.readUtf8())
  }

  @Test fun foldingClosesUnderlyingSinkWhenPipeSinkIsClose() {
    val pipe = Pipe(128)

    val pipeSink = pipe.sink.buffer()
    pipeSink.writeUtf8("world")
    pipeSink.close()

    val foldedSinkBuffer = Buffer()
    var foldedSinkClosed = false
    val foldedSink = object : ForwardingSink(foldedSinkBuffer) {
      override fun close() {
        foldedSinkClosed = true
        super.close()
      }
    }

    pipe.fold(foldedSink)
    assertEquals("world", foldedSinkBuffer.readUtf8(5))
    assertTrue(foldedSinkClosed)
  }

  private fun assertDuration(expected: Long, block: () -> Unit) {
    val start = System.currentTimeMillis()
    block()
    val elapsed = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis() - start)

    assertEquals(expected.toDouble(), elapsed.toDouble(),
      TimeUnit.MILLISECONDS.toNanos(200).toDouble())
  }

  /** Writes on this sink never complete. They can only time out. */
  class TimeoutWritingSink : Sink {
    val timeout = object : AsyncTimeout() {
      override fun timedOut() {
        synchronized(this@TimeoutWritingSink) {
          (this@TimeoutWritingSink as Object).notifyAll()
        }
      }
    }

    override fun write(source: Buffer, byteCount: Long) {
      timeout.enter()
      try {
        synchronized(this) {
          (this as Object).wait()
        }
      } finally {
        timeout.exit()
      }
      source.skip(byteCount)
    }

    override fun flush() = Unit

    override fun close() = Unit

    override fun timeout() = timeout
  }

  /** Flushes on this sink never complete. They can only time out. */
  class TimeoutFlushingSink : Sink {
    val timeout = object : AsyncTimeout() {
      override fun timedOut() {
        synchronized(this@TimeoutFlushingSink) {
          (this@TimeoutFlushingSink as Object).notifyAll()
        }
      }
    }

    override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)

    override fun flush() {
      timeout.enter()
      try {
        synchronized(this) {
          (this as Object).wait()
        }
      } finally {
        timeout.exit()
      }
    }

    override fun close() = Unit

    override fun timeout() = timeout
  }

  /** Closes on this sink never complete. They can only time out. */
  class TimeoutClosingSink : Sink {
    val timeout = object : AsyncTimeout() {
      override fun timedOut() {
        synchronized(this@TimeoutClosingSink) {
          (this@TimeoutClosingSink as Object).notifyAll()
        }
      }
    }

    override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)

    override fun flush() = Unit

    override fun close() {
      timeout.enter()
      try {
        synchronized(this) {
          (this as Object).wait()
        }
      } finally {
        timeout.exit()
      }
    }

    override fun timeout() = timeout
  }

  companion object {
    val smallerTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(500L)
    val biggerTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(1500L)

    val smallerDeadlineNanos = TimeUnit.MILLISECONDS.toNanos(500L)
    val biggerDeadlineNanos = TimeUnit.MILLISECONDS.toNanos(1500L)
  }
}