/*
 * Copyright (C) 2014 Square, Inc. and others.
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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ByteStringReadWriteBenchmark {

  @Param({ "1000000" })
  int size;

  public static final File OriginPath =
      new File(System.getProperty("okio.bench.origin.path", "/dev/zero"));

  public static final File DestinationPath =
      new File(System.getProperty("okio.bench.dest.path", "/dev/null"));

  @Benchmark
  public void byteStringReadAndWrite() throws IOException {
    try (BufferedSource src = Okio.buffer(Okio.source(OriginPath))) {
      try (BufferedSink sink = Okio.buffer(Okio.sink(DestinationPath))) {
        sink.write(src.readByteString(size));
      }
    }
  }
}
