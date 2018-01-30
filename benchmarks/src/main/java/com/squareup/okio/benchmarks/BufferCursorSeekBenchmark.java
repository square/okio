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
public class BufferCursorSeekBenchmark {
  Buffer buffer;
  Buffer.UnsafeCursor cursor;

  @Param({ "2097152" })
  int bufferSize; // 2 MB = 256 Segments

  @Setup
  public void setup() throws IOException {
    byte[] source = new byte[8192];
    buffer = new Buffer();
    while (buffer.size() < bufferSize) {
      buffer.write(source);
    }
    cursor = new Buffer.UnsafeCursor();
  }

  @Benchmark
  public void seekBeginning() {
    buffer.readUnsafe(cursor);
    try {
      cursor.seek(0);
    } finally {
      cursor.close();
    }
  }

  @Benchmark
  public void seekEnd() {
    buffer.readUnsafe(cursor);
    try {
      cursor.seek(buffer.size() - 1);
    } finally {
      cursor.close();
    }
  }

  @Benchmark
  public void seekForward() {
    buffer.readUnsafe(cursor);
    try {
      cursor.seek(0);
      cursor.seek(1);
    } finally {
      cursor.close();
    }
  }

  @Benchmark
  public void seekBackward() {
    buffer.readUnsafe(cursor);
    try {
      cursor.seek(buffer.size() - 1);
      cursor.seek(buffer.size() - 2);
    } finally {
      cursor.close();
    }
  }

  public static void main(String[] args) throws IOException, RunnerException {
    Main.main(new String[] {
        BufferCursorSeekBenchmark.class.getName()
    });
  }
}
