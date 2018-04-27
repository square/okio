/*
 * Copyright (C) 2016 Square, Inc.
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
package okio;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

import static okio.TestUtil.randomSource;
import static org.junit.Assert.assertEquals;

/** Slow running tests that run a large amount of data through a stream. */
public final class LargeStreamsTest {
  /** 4 GiB plus 1 byte. This is greater than what can be expressed in an unsigned int. */
  public static final long FOUR_GIB_PLUS_ONE = 0x100000001L;

  /** SHA-256 of {@code TestUtil.randomSource(FOUR_GIB_PLUS_ONE)}. */
  public static final ByteString SHA256_RANDOM_FOUR_GIB_PLUS_1 = ByteString.decodeHex(
      "9654947a655c5efc445502fd1bf11117d894b7812b7974fde8ca4a02c5066315");

  @Test public void test() throws Exception {
    Pipe pipe = new Pipe(1024 * 1024);

    Future<Long> future = readAllAndCloseAsync(randomSource(FOUR_GIB_PLUS_ONE), pipe.sink());

    HashingSink hashingSink = HashingSink.sha256(Okio.blackhole());
    readAllAndClose(pipe.source(), hashingSink);

    assertEquals(FOUR_GIB_PLUS_ONE, (long) future.get());
    assertEquals(SHA256_RANDOM_FOUR_GIB_PLUS_1, hashingSink.hash());
  }

  @Test public void gzipSource() throws Exception {
    Pipe pipe = new Pipe(1024 * 1024);

    OutputStream gzipOut = new GZIPOutputStream(Okio.buffer(pipe.sink()).outputStream()) {
      {
        // Disable compression to speed up a slow test. Improved from 141s to 33s on one machine.
        def.setLevel(Deflater.NO_COMPRESSION);
      }
    };
    Future<Long> future = readAllAndCloseAsync(
        randomSource(FOUR_GIB_PLUS_ONE), Okio.sink(gzipOut));

    HashingSink hashingSink = HashingSink.sha256(Okio.blackhole());
    GzipSource gzipSource = new GzipSource(pipe.source());
    readAllAndClose(gzipSource, hashingSink);

    assertEquals(FOUR_GIB_PLUS_ONE, (long) future.get());
    assertEquals(SHA256_RANDOM_FOUR_GIB_PLUS_1, hashingSink.hash());
  }

  @Test public void gzipSink() throws Exception {
    Pipe pipe = new Pipe(1024 * 1024);

    GzipSink gzipSink = new GzipSink(pipe.sink());

    // Disable compression to speed up a slow test. Improved from 141s to 35s on one machine.
    gzipSink.deflater().setLevel(Deflater.NO_COMPRESSION);
    Future<Long> future = readAllAndCloseAsync(randomSource(FOUR_GIB_PLUS_ONE), gzipSink);

    HashingSink hashingSink = HashingSink.sha256(Okio.blackhole());
    GZIPInputStream gzipIn = new GZIPInputStream(Okio.buffer(pipe.source()).inputStream());
    readAllAndClose(Okio.source(gzipIn), hashingSink);

    assertEquals(FOUR_GIB_PLUS_ONE, (long) future.get());
    assertEquals(SHA256_RANDOM_FOUR_GIB_PLUS_1, hashingSink.hash());
  }

  /** Reads all bytes from {@code source} and writes them to {@code sink}. */
  private Long readAllAndClose(Source source, Sink sink) throws IOException {
    long result = 0L;
    Buffer buffer = new Buffer();
    for (long count; (count = source.read(buffer, Segment.SIZE)) != -1L; result += count) {
      sink.write(buffer, count);
    }
    source.close();
    sink.close();
    return result;
  }

  /** Calls {@link #readAllAndClose} on a background thread. */
  private Future<Long> readAllAndCloseAsync(final Source source, final Sink sink) {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      return executor.submit(new Callable<Long>() {
        @Override public Long call() throws Exception {
          return readAllAndClose(source, sink);
        }
      });
    } finally {
      executor.shutdown();
    }
  }
}
