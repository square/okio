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

import okio.TestUtil.randomSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.test.assertEquals

class ThrottlerTest {
  private val size = 1024L * 80L // 80 KiB
  private val source = randomSource(size)

  private val throttler = Throttler()
  private val throttlerSlow = Throttler()

  private val threads = 4
  private val executorService = Executors.newFixedThreadPool(threads)
  private var stopwatch = Stopwatch()

  @Before fun setup() {
    throttler.bytesPerSecond(4 * size, 4096, 8192)
    throttlerSlow.bytesPerSecond(2 * size, 4096, 8192)
    stopwatch = Stopwatch()
  }

  @After fun teardown() {
    executorService.shutdown()
  }

  @Test fun sourceByteCount() {
    throttler.bytesPerSecond(bytesPerSecond = 20, waitByteCount = 5, maxByteCount = 10)
    val source = throttler.source(source)
    val buffer = Buffer()

    // We get the first 10 bytes immediately (that's maxByteCount).
    assertThat(source.read(buffer, size)).isEqualTo(10)
    stopwatch.assertElapsed(0.0)

    // Wait a quarter second for each subsequent 5 bytes (that's waitByteCount).
    assertThat(source.read(buffer, size)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)

    assertThat(source.read(buffer, size)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)

    // Wait three quarters of a second to build up 15 bytes of potential.
    // Since maxByteCount = 10, there will only be 10 bytes of potential.
    Thread.sleep(750)
    stopwatch.assertElapsed(0.75)

    // We get 10 bytes immediately (that's maxByteCount again).
    assertThat(source.read(buffer, size)).isEqualTo(10)
    stopwatch.assertElapsed(0.0)

    // Wait a quarter second for each subsequent 5 bytes (that's waitByteCount again).
    assertThat(source.read(buffer, size)).isEqualTo(5)
    stopwatch.assertElapsed(0.25)
  }

  @Test fun sinkByteCount() {
    throttler.bytesPerSecond(bytesPerSecond = 20, waitByteCount = 5, maxByteCount = 10)
    val sinkBuffer = Buffer()
    val sourceBuffer = Buffer().apply { writeAll(source) }
    val sink = throttler.sink(sinkBuffer)
    var written = 0L

    // We write the first 10 bytes immediately (that's maxByteCount again).
    sink.write(sourceBuffer, 10)
    written += 10
    assertEquals(written, size - sourceBuffer.size)
    stopwatch.assertElapsed(0.0)

    // Wait a quarter second for each subsequent 5 bytes (that's waitByteCount).
    sink.write(sourceBuffer, 5)
    written += 5
    assertEquals(written, size - sourceBuffer.size)
    stopwatch.assertElapsed(0.25)

    // Wait a half second for 10 bytes.
    sink.write(sourceBuffer, 10)
    written += 10
    assertEquals(written, size - sourceBuffer.size)
    stopwatch.assertElapsed(0.5)

    // Wait a three quarters of a second to build up 15 bytes of potential.
    // Since maxByteCount = 10, there will only be 10 bytes of potential.
    Thread.sleep(750)
    stopwatch.assertElapsed(0.75)

    // We write the first 10 bytes immediately (that's maxByteCount again).
    // Wait a quarter second for each subsequent 5 bytes (that's waitByteCount again).
    sink.write(sourceBuffer, 15)
    written += 15
    assertEquals(written, size - sourceBuffer.size)
    stopwatch.assertElapsed(0.25)
  }

  @Test fun source() {
    throttler.source(source).buffer().readAll(blackholeSink())
    stopwatch.assertElapsed(0.25)
  }

  @Test fun sink() {
    source.buffer().readAll(throttler.sink(blackholeSink()))
    stopwatch.assertElapsed(0.25)
  }

  @Test fun sourceAfterClear() {
    throttler.bytesPerSecond(0)
    throttler.source(source).buffer().readAll(blackholeSink())
    stopwatch.assertElapsed(0.0)
  }

  @Test fun sinkAfterClear() {
    throttler.bytesPerSecond(0)
    source.buffer().readAll(throttler.sink(blackholeSink()))
    stopwatch.assertElapsed(0.0)
  }

  @Test fun doubleSourceThrottle() {
    throttler.source(throttler.source(source)).buffer().readAll(blackholeSink())
    stopwatch.assertElapsed(0.5)
  }

  @Test fun doubleSinkThrottle() {
    source.buffer().readAll(throttler.sink(throttler.sink(blackholeSink())))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun singleSourceMultiThrottleSlowerThenSlow() {
    source.buffer().readAll(throttler.sink(throttlerSlow.sink(blackholeSink())))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun singleSourceMultiThrottleSlowThenSlower() {
    source.buffer().readAll(throttlerSlow.sink(throttler.sink(blackholeSink())))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun slowSourceSlowerSink() {
    throttler.source(source).buffer().readAll(throttlerSlow.sink(blackholeSink()))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun slowSinkSlowerSource() {
    throttlerSlow.source(source).buffer().readAll(throttler.sink(blackholeSink()))
    stopwatch.assertElapsed(0.5)
  }

  @Test fun parallel() {
    val futures = List(threads) {
      executorService.submit {
        val source = randomSource(size)
        source.buffer().readAll(throttler.sink(blackholeSink()))
      }
    }
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(1.0)
  }

  @Test fun parallelAfterClear() {
    throttler.bytesPerSecond(0)

    val futures = List(threads) {
      executorService.submit {
        val source = randomSource(size)
        source.buffer().readAll(throttler.sink(blackholeSink()))
      }
    }
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(0.0)
  }

  @Test fun parallelWithClear() {
    val futures = List(threads) {
      executorService.submit {
        val source = randomSource(size)
        source.buffer().readAll(throttler.sink(blackholeSink()))
      }
    }
    Thread.sleep(500)
    throttler.bytesPerSecond(0)
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(0.5)
  }

  @Test fun parallelFastThenSlower() {
    val futures = List(threads) {
      executorService.submit {
        val source = randomSource(size)
        source.buffer().readAll(throttler.sink(blackholeSink()))
      }
    }
    Thread.sleep(500)
    throttler.bytesPerSecond(2 * size)
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(1.5)
  }

  @Test fun parallelSlowThenFaster() {
    val futures = List(threads) {
      executorService.submit {
        val source = randomSource(size)
        source.buffer().readAll(throttlerSlow.sink(blackholeSink()))
      }
    }
    Thread.sleep(1_000)
    throttlerSlow.bytesPerSecond(4 * size)
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(1.5)
  }

  @Test fun parallelIndividualThrottle() {
    val futures = List(threads) {
      executorService.submit {
        val throttlerLocal = Throttler()
        throttlerLocal.bytesPerSecond(4 * size, maxByteCount = 8192)

        val source = randomSource(size)
        source.buffer().readAll(throttlerLocal.sink(blackholeSink()))
      }
    }
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(0.25)
  }

  @Test fun parallelGroupAndIndividualThrottle() {
    val futures = List(threads) {
      executorService.submit {
        val throttlerLocal = Throttler()
        throttlerLocal.bytesPerSecond(4 * size, maxByteCount = 8192)

        val source = randomSource(size)
        source.buffer().readAll(throttler.sink(throttlerLocal.sink(blackholeSink())))
      }
    }
    for (future in futures) {
      future.get()
    }
    stopwatch.assertElapsed(1.0)
  }
}
