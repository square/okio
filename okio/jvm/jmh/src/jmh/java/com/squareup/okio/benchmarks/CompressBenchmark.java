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

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.DeflaterSink;
import okio.FileSystem;
import okio.GzipSink;
import okio.GzipSource;
import okio.InflaterSource;
import okio.Okio;
import okio.Path;
import okio.Sink;
import okio.Source;
import okio.zstd.Zstd;
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
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CompressBenchmark {
  private Path root = Path.get("/Volumes/Development/cash-android", false);
  private FileSystem fileSystem = FileSystem.SYSTEM;

  private byte[] uncompressedData;
  private byte[] compressedData;

  @Param({"deflate", "gzip", "zstd", "none"})
  String algorithm;

  @Param({"8388608"}) // 8 MiB.
  int sampleDataSize;

  @Setup
  public void setup() throws IOException {
    uncompressedData = generateSampleData();

    Buffer compressedBuffer = new Buffer();
    try (BufferedSink sink = Okio.buffer(compress(compressedBuffer))) {
      sink.write(uncompressedData);
    }
    compressedData = compressedBuffer.readByteArray();

    // Confirm the compression is round-trip.
    Buffer validateSource = new Buffer();
    validateSource.write(compressedData);
    byte[] decompressedData;
    try (BufferedSource source = Okio.buffer(decompress(validateSource))) {
      decompressedData = source.readByteArray();
    }

    if (!Arrays.equals(uncompressedData, decompressedData)) {
      throw new IllegalStateException("failed to round trip " + algorithm);
    }
  }

  @Benchmark
  public void compress() throws IOException {
    try (BufferedSink sink = Okio.buffer(compress(Okio.blackhole()))) {
      sink.write(uncompressedData);
    }
  }

  @Benchmark
  public void decompress() throws IOException {
    Buffer compressedBuffer = new Buffer();
    compressedBuffer.write(compressedData);
    try (BufferedSource source = Okio.buffer(decompress(compressedBuffer))) {
      source.readAll(Okio.blackhole());
    }
  }

  private Sink compress(Sink delegate) {
    if (algorithm.equals("deflate")) {
      return new DeflaterSink(delegate, new Deflater());
    } else if (algorithm.equals("gzip")) {
      return new GzipSink(delegate);
    } else if (algorithm.equals("zstd")) {
      return Zstd.zstdCompress(delegate);
    } else if (algorithm.equals("none")) {
      return delegate;
    } else {
      throw new IllegalArgumentException("unexpected algorithm: " + algorithm);
    }
  }

  private Source decompress(Source delegate) {
    if (algorithm.equals("deflate")) {
      return new InflaterSource(delegate, new Inflater());
    } else if (algorithm.equals("gzip")) {
      return new GzipSource(delegate);
    } else if (algorithm.equals("zstd")) {
      return Zstd.zstdDecompress(delegate);
    } else if (algorithm.equals("none")) {
      return delegate;
    } else {
      throw new IllegalArgumentException("unexpected algorithm: " + algorithm);
    }
  }

  private byte[] generateSampleData() throws IOException {
    Buffer sampleDataBuffer = new Buffer();
    Iterator<Path> pathIterator = fileSystem.listRecursively(root).iterator();
    while (sampleDataBuffer.size() < sampleDataSize) {
      Path path = pathIterator.next();
      if (path.name().endsWith(".kt")) {
        Source source = fileSystem.source(path);
        try {
          sampleDataBuffer.writeAll(source);
        } finally {
          source.close();
        }
      }
    }
    return sampleDataBuffer.readByteArray(sampleDataSize);
  }
}
