/*
 * Copyright (C) 2014 Square, Inc.
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

import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static kotlin.text.StringsKt.repeat;
import static okio.TestUtil.SEGMENT_SIZE;
import static okio.TestUtil.randomBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

@RunWith(Parameterized.class)
public final class InflaterSourceTest {
  /**
   * Use a parameterized test to control how many bytes the InflaterSource gets with each request
   * for more bytes.
   */
  @Parameters(name = "{0}")
  public static List<Object[]> parameters() {
    return BufferedSourceFactory.Companion.getPARAMETERIZED_TEST_VALUES();
  }

  public final BufferedSourceFactory bufferFactory;
  public BufferedSink deflatedSink;
  public BufferedSource deflatedSource;

  public InflaterSourceTest(BufferedSourceFactory bufferFactory) {
    this.bufferFactory = bufferFactory;
    resetDeflatedSourceAndSink();
  }

  private void resetDeflatedSourceAndSink() {
    BufferedSourceFactory.Pipe pipe = bufferFactory.pipe();
    this.deflatedSink = pipe.getSink();
    this.deflatedSource = pipe.getSource();
  }

  @Test public void inflate() throws Exception {
    decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=");
    Buffer inflated = inflate(deflatedSource);
    assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8());
  }

  @Test public void inflateTruncated() throws Exception {
    decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CDw==");
    try {
      inflate(deflatedSource);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void inflateWellCompressed() throws Exception {
    decodeBase64("eJztwTEBAAAAwqCs61/CEL5AAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB8BtFeWvE=");
    String original = repeat("a", 1024 * 1024);
    deflate(ByteString.encodeUtf8(original));
    Buffer inflated = inflate(deflatedSource);
    assertEquals(original, inflated.readUtf8());
  }

  @Test public void inflatePoorlyCompressed() throws Exception {
    assumeFalse(bufferFactory.isOneByteAtATime()); // 8 GiB for 1 byte per segment!

    ByteString original = randomBytes(1024 * 1024);
    deflate(original);
    Buffer inflated = inflate(deflatedSource);
    assertEquals(original, inflated.readByteString());
  }

  @Test public void inflateIntoNonemptySink() throws Exception {
    for (int i = 0; i < SEGMENT_SIZE; i++) {
      resetDeflatedSourceAndSink();
      Buffer inflated = new Buffer().writeUtf8(repeat("a", i));
      deflate(ByteString.encodeUtf8("God help us, we're in the hands of engineers."));
      InflaterSource source = new InflaterSource(deflatedSource, new Inflater());
      while (source.read(inflated, Integer.MAX_VALUE) != -1) {
      }
      inflated.skip(i);
      assertEquals("God help us, we're in the hands of engineers.", inflated.readUtf8());
    }
  }

  @Test public void inflateSingleByte() throws Exception {
    Buffer inflated = new Buffer();
    decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=");
    InflaterSource source = new InflaterSource(deflatedSource, new Inflater());
    source.read(inflated, 1);
    source.close();
    assertEquals("G", inflated.readUtf8());
    assertEquals(0, inflated.size());
  }

  @Test public void inflateByteCount() throws Exception {
    assumeFalse(bufferFactory.isOneByteAtATime()); // This test assumes one step.

    Buffer inflated = new Buffer();
    decodeBase64("eJxzz09RyEjNKVAoLdZRKE9VL0pVyMxTKMlIVchIzEspVshPU0jNS8/MS00tKtYDAF6CD5s=");
    InflaterSource source = new InflaterSource(deflatedSource, new Inflater());
    source.read(inflated, 11);
    source.close();
    assertEquals("God help us", inflated.readUtf8());
    assertEquals(0, inflated.size());
  }

  @Test public void sourceExhaustedPrematurelyOnRead() throws Exception {
    // Deflate 0 bytes of data that lacks the in-stream terminator.
    decodeBase64("eJwAAAD//w==");

    Buffer inflated = new Buffer();
    Inflater inflater = new Inflater();
    InflaterSource source = new InflaterSource(deflatedSource, inflater);
    assertThat(deflatedSource.exhausted()).isFalse();
    try {
      source.read(inflated, Long.MAX_VALUE);
      fail();
    } catch (EOFException expected) {
      assertThat(expected).hasMessage("source exhausted prematurely");
    }

    // Despite the exception, the read() call made forward progress on the underlying stream!
    assertThat(deflatedSource.exhausted()).isTrue();
  }

  /**
   * Confirm that {@link InflaterSource#readOrInflate} consumes a byte on each call even if it
   * doesn't produce a byte on every call.
   */
  @Test public void readOrInflateMakesByteByByteProgress() throws Exception {
    // Deflate 0 bytes of data that lacks the in-stream terminator.
    decodeBase64("eJwAAAD//w==");
    int deflatedByteCount = 7;

    Buffer inflated = new Buffer();
    Inflater inflater = new Inflater();
    InflaterSource source = new InflaterSource(deflatedSource, inflater);
    assertThat(deflatedSource.exhausted()).isFalse();

    if (bufferFactory.isOneByteAtATime()) {
      for (int i = 0; i < deflatedByteCount; i++) {
        assertThat(inflater.getBytesRead()).isEqualTo(i);
        assertThat(source.readOrInflate(inflated, Long.MAX_VALUE)).isEqualTo(0L);
      }
    } else {
      assertThat(source.readOrInflate(inflated, Long.MAX_VALUE)).isEqualTo(0L);
    }

    assertThat(inflater.getBytesRead()).isEqualTo(deflatedByteCount);
    assertThat(deflatedSource.exhausted());
  }

  private void decodeBase64(String s) throws IOException {
    deflatedSink.write(ByteString.decodeBase64(s));
    deflatedSink.flush();
  }

  /** Use DeflaterOutputStream to deflate source. */
  private void deflate(ByteString source) throws IOException {
    Sink sink = Okio.sink(new DeflaterOutputStream(deflatedSink.outputStream()));
    sink.write(new Buffer().write(source), source.size());
    sink.close();
  }

  /** Returns a new buffer containing the inflated contents of {@code deflated}. */
  private Buffer inflate(BufferedSource deflated) throws IOException {
    Buffer result = new Buffer();
    InflaterSource source = new InflaterSource(deflated, new Inflater());
    while (source.read(result, Integer.MAX_VALUE) != -1) {
    }
    return result;
  }
}
