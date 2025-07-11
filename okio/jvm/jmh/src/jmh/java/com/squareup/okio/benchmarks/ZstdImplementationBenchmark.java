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

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import org.jetbrains.annotations.NotNull;
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

/** Confirm Okio Zstd has performance consistent with Zstd-jni. */
@Fork(1)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ZstdImplementationBenchmark {
  private SampleData sampleData;

  @Param({"8388608"}) // 8 MiB.
  int sampleDataSize;

  @Setup
  public void setup() throws IOException {
    sampleData = SampleData.create("zstd", sampleDataSize);
  }

  @Benchmark
  public void okioCompress() throws IOException {
    try (BufferedSink sink = Okio.buffer(sampleData.compress("zstd", Okio.blackhole()))) {
      sink.write(sampleData.uncompressedData);
    }
  }

  @Benchmark
  public void zstdJniCompress() throws IOException {
    try (OutputStream out = new ZstdOutputStream(new BlackholeOutputStream())) {
      out.write(sampleData.uncompressedData);
    }
  }

  @Benchmark
  public void okioDecompress() throws IOException {
    Buffer compressedBuffer = new Buffer();
    compressedBuffer.write(sampleData.compressedData);
    try (BufferedSource source = Okio.buffer(sampleData.decompress("zstd", compressedBuffer))) {
      source.readAll(Okio.blackhole());
    }
  }

  @Benchmark
  public void zstdJniDecompress() throws IOException {
    byte[] blackhole = new byte[8192];
    InputStream compressedInputStream = new ByteArrayInputStream(sampleData.compressedData);
    try (InputStream in = new ZstdInputStream(compressedInputStream)) {
      while (true) {
        if (in.read(blackhole) == -1) break;
      }
    }
  }

  static class BlackholeOutputStream extends OutputStream {
    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
    }

    @Override
    public void write(int b) throws IOException {
    }

    @Override
    public void write(@NotNull byte[] b) throws IOException {
    }
  }
}
