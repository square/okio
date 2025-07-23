/*
 * Copyright (C) 2025 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.concurrent.TimeUnit;
import okio.AsyncTimeout;
import okio.PriorityQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(1)
@Warmup(iterations = 2, time = 2)
@Measurement(iterations = 3, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class AsyncTimeoutBenchmark {

  @Param({ "1", "10", "100", "1000" })
  int queueSize;

  private List<AsyncTimeout> asyncTimeouts;
  private PriorityQueue heap = new PriorityQueue();
  private int next = 0;

  @Setup
  public void setup() {
    heap = new PriorityQueue();
    asyncTimeouts = new ArrayList<>(queueSize);
    for (int i = 0; i < queueSize; i++) {
      AsyncTimeout timeout = new AsyncTimeout() {
        @Override protected void timedOut() {
          // No-op
        }
      };
      timeout.timeout(i, TimeUnit.SECONDS);
      asyncTimeouts.add(timeout);
    }
    Collections.shuffle(asyncTimeouts, new Random(0));
    for (int i = 0; i < queueSize; i++) {
      heap.add(asyncTimeouts.get(i));
    }
  }

  @Benchmark
  public void enterExit() {
    AsyncTimeout timeout = asyncTimeouts.get(next++);
    heap.remove(timeout);
    heap.add(timeout);
    next = next % queueSize;
  }
}
