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
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.RunnerException;

@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ReadByteStringBenchmark {

  Buffer buffer;

  @Param({"32768"})
  int bufferSize;

  @Param({"8", "16", "32", "64", "128", "256", "512", "1024", "2048", "4096", "8192", "16384",
      "32768"})
  int byteStringSize;

  @Setup
  public void setup() {
    buffer = new Buffer().write(new byte[bufferSize]);
  }

  @Benchmark
  public void readByteString() throws IOException {
    buffer.write(buffer.readByteString(byteStringSize));
  }

  @Benchmark
  public void readByteString_toByteArray() throws IOException {
    buffer.write(buffer.readByteString(byteStringSize).toByteArray());
  }

  public static void main(String[] args) throws IOException, RunnerException {
    Main.main(new String[]{
        ReadByteStringBenchmark.class.getName()
    });
  }
}
