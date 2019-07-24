/*
 * Copyright (C) 2019 Square, Inc. and others.
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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okio.Buffer;
import org.openjdk.jmh.Main;
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
import org.openjdk.jmh.runner.RunnerException;

@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class WriteHexadecimalBenchmark {

  Buffer buffer;

  @Param({"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16"})
  int width;

  @Setup
  public void setup() {
    buffer = new Buffer();
  }

  @TearDown(Level.Invocation)
  public void teardown() {
    buffer.clear();
  }

  @Benchmark
  public void writeHex() {
    buffer.writeHexadecimalUnsignedLong(1L << width);
  }

  public static void main(String[] args) throws IOException, RunnerException {
    Main.main(new String[]{
        WriteHexadecimalBenchmark.class.getName()
    });
  }
}
