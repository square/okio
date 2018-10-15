/*
 * Copyright (C) 2018 Square, Inc. and others.
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
import okio.ByteString;
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
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class SegmentedByteStringBenchmark {

  private static final ByteString UNKNOWN = ByteString.encodeUtf8("UNKNOWN");
  private static final ByteString SEARCH = ByteString.encodeUtf8("tell");

  @Param({"20", "2000", "200000"})
  int length;

  private ByteString byteString;

  @Setup
  public void setup() {
    String part =
        "Um, I'll tell you the problem with the scientific power that you're using here, "
            + "it didn't require any discipline to attain it. You read what others had done and you "
            + "took the next step. You didn't earn the knowledge for yourselves, so you don't take any "
            + "responsibility for it. You stood on the shoulders of geniuses to accomplish something "
            + "as fast as you could, and before you even knew what you had, you patented it, and "
            + "packaged it, and slapped it on a plastic lunchbox, and now you're selling it, you wanna "
            + "sell it.";

    Buffer buffer = new Buffer();
    while (buffer.size() < length) {
      buffer.writeUtf8(part);
    }
    byteString = buffer.snapshot(length);
  }

  @Benchmark
  public ByteString substring() {
    return byteString.substring(1, byteString.size() - 1);
  }

  @Benchmark
  public ByteString md5() {
    return byteString.md5();
  }

  @Benchmark
  public int indexOfUnknown() {
    return byteString.indexOf(UNKNOWN);
  }

  @Benchmark
  public int lastIndexOfUnknown() {
    return byteString.lastIndexOf(UNKNOWN);
  }

  @Benchmark
  public int indexOfEarly() {
    return byteString.indexOf(SEARCH);
  }

  @Benchmark
  public int lastIndexOfEarly() {
    return byteString.lastIndexOf(SEARCH);
  }

  public static void main(String[] args) throws IOException, RunnerException {
    Main.main(new String[] {SegmentedByteStringBenchmark.class.getName()});
  }
}
