/*
 * Copyright (C) 2025 Square, Inc.
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
package com.squareup.okio.benchmarks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import okio.AsyncTimeout;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Real-world AsyncTimeout usage benchmark measuring the actual enter/exit lifecycle.
 *
 * This benchmark focuses on the realistic usage patterns:
 * 1. Creating AsyncTimeout instances
 * 2. Calling enter() which triggers queue insertion and state management
 * 3. Calling exit() which triggers queue cleanup and state transitions
 * 4. Measuring performance under various concurrency levels
 *
 * This exercises the actual lock-free queue implementation with proper
 * AsyncTimeout lifecycle management, including tombstoning and cleanup.
 */
@Fork(2)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class AsyncTimeoutActualBenchmark {

  @Param({ "1", "2", "4", "8", "16" })
  int threadCount;

  @Param({ "100", "500", "1000" })
  int operationsPerThread;

  private List<AsyncTimeout> timeoutPool;

  @Setup(Level.Trial)
  public void setup() {
    // Create a pool of AsyncTimeout instances with varied timeout values
    timeoutPool = new ArrayList<>(operationsPerThread * threadCount * 2);
    Random random = new Random(42); // Fixed seed for reproducibility

    for (int i = 0; i < operationsPerThread * threadCount * 2; i++) {
      AsyncTimeout timeout = new AsyncTimeout() {
        @Override protected void timedOut() {
          // No-op for benchmarking
        }
      };

      // Create realistic timeout values (10ms to 30 seconds)
      long timeoutMs = 10 + random.nextInt(30000);
      timeout.timeout(timeoutMs, TimeUnit.MILLISECONDS);
      timeoutPool.add(timeout);
    }

    Collections.shuffle(timeoutPool, random);
  }

  /**
   * Baseline: Single-threaded enter/exit operations
   */
  @Benchmark
  public void actualEnterExit(Blackhole bh) {
    for (int i = 0; i < 100; i++) {
      AsyncTimeout timeout = timeoutPool.get(i % timeoutPool.size());

      timeout.enter();
      boolean timedOut = timeout.exit();
      bh.consume(timedOut);
    }
  }

  /**
   * Concurrent enter/exit operations with 1 thread
   */
  @Benchmark
  public void concurrent1Thread() throws InterruptedException {
    runConcurrentEnterExit(1);
  }

  /**
   * Concurrent enter/exit operations with 2 threads
   */
  @Benchmark
  public void concurrent2Threads() throws InterruptedException {
    runConcurrentEnterExit(2);
  }

  /**
   * Concurrent enter/exit operations with 4 threads
   */
  @Benchmark
  public void concurrent4Threads() throws InterruptedException {
    runConcurrentEnterExit(4);
  }

  /**
   * Concurrent enter/exit operations with 8 threads
   */
  @Benchmark
  public void concurrent8Threads() throws InterruptedException {
    runConcurrentEnterExit(8);
  }

  /**
   * Concurrent enter/exit operations with 16 threads
   */
  @Benchmark
  public void concurrent16Threads() throws InterruptedException {
    runConcurrentEnterExit(16);
  }

  /**
   * Mixed workload with enter/exit and cancellations
   */
  @Benchmark
  public void mixedWorkloadWithCancellation() throws InterruptedException {
    runMixedWorkload(threadCount);
  }

  /**
   * High contention scenario where many threads compete
   */
  @Benchmark
  public void highContentionEnterExit() throws InterruptedException {
    runHighContentionTest(threadCount);
  }

  // Helper method for concurrent enter/exit testing
  private void runConcurrentEnterExit(int threads) throws InterruptedException {
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      Thread thread = new Thread(() -> {
        try {
          startLatch.await();
          ThreadLocalRandom random = ThreadLocalRandom.current();

          for (int i = 0; i < operationsPerThread; i++) {
            AsyncTimeout timeout = timeoutPool.get(random.nextInt(timeoutPool.size()));

            timeout.enter();
            // Simulate some work
            Thread.yield();
            timeout.exit();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          doneLatch.countDown();
        }
      });
      thread.start();
    }

    startLatch.countDown();
    doneLatch.await();
  }

  // Mixed workload test: enter/exit with cancellations
  private void runMixedWorkload(int threads) throws InterruptedException {
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      Thread thread = new Thread(() -> {
        try {
          startLatch.await();
          ThreadLocalRandom random = ThreadLocalRandom.current();

          for (int i = 0; i < operationsPerThread; i++) {
            AsyncTimeout timeout = timeoutPool.get(random.nextInt(timeoutPool.size()));

            timeout.enter();

            // 30% chance of cancellation before exit
            if (random.nextInt(100) < 30) {
              timeout.cancel();
            }

            // Simulate some work
            if (random.nextInt(100) < 10) {
              Thread.yield();
            }

            timeout.exit();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          doneLatch.countDown();
        }
      });
      thread.start();
    }

    startLatch.countDown();
    doneLatch.await();
  }

  // High contention test using small timeout pool
  private void runHighContentionTest(int threads) throws InterruptedException {
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threads);

    // Use smaller pool for higher contention on same timeout objects
    final int contentionPoolSize = Math.max(10, timeoutPool.size() / 20);

    for (int t = 0; t < threads; t++) {
      Thread thread = new Thread(() -> {
        try {
          startLatch.await();
          ThreadLocalRandom random = ThreadLocalRandom.current();

          for (int i = 0; i < operationsPerThread / 2; i++) { // Fewer ops for contention test
            AsyncTimeout timeout = timeoutPool.get(random.nextInt(contentionPoolSize));

            timeout.enter();

            // Increased chance of cancellation for more state transitions
            if (random.nextInt(100) < 50) {
              timeout.cancel();
            }

            timeout.exit();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          doneLatch.countDown();
        }
      });
      thread.start();
    }

    startLatch.countDown();
    doneLatch.await();
  }
}