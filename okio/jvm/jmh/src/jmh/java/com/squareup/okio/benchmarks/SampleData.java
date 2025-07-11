package com.squareup.okio.benchmarks;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
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

public class SampleData {
  private static final Path root = Path.get("/Volumes/Development/cash-android", false);
  private static final FileSystem fileSystem = FileSystem.SYSTEM;

  public final byte[] uncompressedData;
  public final byte[] compressedData;

  public SampleData(byte[] uncompressedData, byte[] compressedData) {
    this.uncompressedData = uncompressedData;
    this.compressedData = compressedData;
  }

  public static SampleData create(String algorithm, int size) throws IOException {
    byte[] uncompressedData = generateSampleData(size);

    Buffer compressedBuffer = new Buffer();
    try (BufferedSink sink = Okio.buffer(compress(algorithm, compressedBuffer))) {
      sink.write(uncompressedData);
    }
    byte[] compressedData = compressedBuffer.readByteArray();

    // Confirm the compression is round-trip.
    Buffer validateSource = new Buffer();
    validateSource.write(compressedData);
    byte[] decompressedData;
    try (BufferedSource source = Okio.buffer(decompress(algorithm, validateSource))) {
      decompressedData = source.readByteArray();
    }

    if (!Arrays.equals(uncompressedData, decompressedData)) {
      throw new IllegalStateException("failed to round trip " + algorithm);
    }

    return new SampleData(uncompressedData, compressedData);
  }

  private static byte[] generateSampleData(int size) throws IOException {
    Buffer sampleDataBuffer = new Buffer();
    Iterator<Path> pathIterator = fileSystem.listRecursively(root).iterator();
    while (sampleDataBuffer.size() < size) {
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
    return sampleDataBuffer.readByteArray(size);
  }
  static Sink compress(String algorithm, Sink delegate) {
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

  static Source decompress(String algorithm, Source delegate) {
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
}
