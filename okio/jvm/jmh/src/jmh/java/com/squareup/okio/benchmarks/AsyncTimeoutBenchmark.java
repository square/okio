package com.squareup.okio.benchmarks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import okio.AsyncTimeout;
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
import org.openjdk.jmh.annotations.TearDown;
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

  List<AsyncTimeout> asyncTimeouts;

  int next = 0;

  @Setup
  public void setup() {
    asyncTimeouts = new ArrayList<>(queueSize);
    for (int i = 0; i < queueSize; i++) {
      AsyncTimeout timeout = new AsyncTimeout() {
        @Override protected void timedOut() {
          // No-op
        }
      };
      timeout.timeout(3600 + i, TimeUnit.SECONDS); // Never time out, insert in order.
      asyncTimeouts.add(timeout);
    }
    Collections.shuffle(asyncTimeouts, new Random(0));
    for (int i = 0; i < queueSize; i++) {
      asyncTimeouts.get(i).enter();
    }
  }

  @TearDown
  public void tearDown() {
    for (AsyncTimeout timeout : asyncTimeouts) {
      timeout.exit();
    }
  }

  @Benchmark
  public void enterExit() {
    AsyncTimeout timeout = asyncTimeouts.get(next++);
    timeout.exit();
    timeout.enter();
    next = next % queueSize;
  }
}
