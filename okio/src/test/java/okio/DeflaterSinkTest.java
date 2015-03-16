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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import org.junit.Test;

import static okio.TestUtil.randomBytes;
import static okio.TestUtil.repeat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class DeflaterSinkTest {
  @Test public void deflateWithClose() throws Exception {
    Buffer data = new Buffer();
    String original = "They're moving in herds. They do move in herds.";
    data.writeUtf8(original);
    Buffer sink = new Buffer();
    DeflaterSink deflaterSink = new DeflaterSink(sink, new Deflater());
    deflaterSink.write(data, data.size());
    deflaterSink.close();
    Buffer inflated = inflate(sink);
    assertEquals(original, inflated.readUtf8());
  }

  @Test public void deflateWithSyncFlush() throws Exception {
    String original = "Yes, yes, yes. That's why we're taking extreme precautions.";
    Buffer data = new Buffer();
    data.writeUtf8(original);
    Buffer sink = new Buffer();
    DeflaterSink deflaterSink = new DeflaterSink(sink, new Deflater());
    deflaterSink.write(data, data.size());
    deflaterSink.flush();
    Buffer inflated = inflate(sink);
    assertEquals(original, inflated.readUtf8());
  }

  @Test public void deflateWellCompressed() throws IOException {
    String original = repeat('a', 1024 * 1024);
    Buffer data = new Buffer();
    data.writeUtf8(original);
    Buffer sink = new Buffer();
    DeflaterSink deflaterSink = new DeflaterSink(sink, new Deflater());
    deflaterSink.write(data, data.size());
    deflaterSink.close();
    Buffer inflated = inflate(sink);
    assertEquals(original, inflated.readUtf8());
  }

  @Test public void deflatePoorlyCompressed() throws IOException {
    ByteString original = randomBytes(1024 * 1024);
    Buffer data = new Buffer();
    data.write(original);
    Buffer sink = new Buffer();
    DeflaterSink deflaterSink = new DeflaterSink(sink, new Deflater());
    deflaterSink.write(data, data.size());
    deflaterSink.close();
    Buffer inflated = inflate(sink);
    assertEquals(original, inflated.readByteString());
  }

  @Test public void multipleSegmentsWithoutCompression() throws IOException {
    Buffer buffer = new Buffer();
    Deflater deflater = new Deflater();
    deflater.setLevel(Deflater.NO_COMPRESSION);
    DeflaterSink deflaterSink = new DeflaterSink(buffer, deflater);
    int byteCount = Segment.SIZE * 4;
    deflaterSink.write(new Buffer().writeUtf8(repeat('a', byteCount)), byteCount);
    deflaterSink.close();
    assertEquals(repeat('a', byteCount), inflate(buffer).readUtf8(byteCount));
  }

  @Test public void deflateIntoNonemptySink() throws Exception {
    String original = "They're moving in herds. They do move in herds.";

    // Exercise all possible offsets for the outgoing segment.
    for (int i = 0; i < Segment.SIZE; i++) {
      Buffer data = new Buffer().writeUtf8(original);
      Buffer sink = new Buffer().writeUtf8(repeat('a', i));

      DeflaterSink deflaterSink = new DeflaterSink(sink, new Deflater());
      deflaterSink.write(data, data.size());
      deflaterSink.close();

      sink.skip(i);
      Buffer inflated = inflate(sink);
      assertEquals(original, inflated.readUtf8());
    }
  }

  /**
   * This test deflates a single segment of without compression because that's
   * the easiest way to force close() to emit a large amount of data to the
   * underlying sink.
   */
  @Test public void closeWithExceptionWhenWritingAndClosing() throws IOException {
    MockSink mockSink = new MockSink();
    mockSink.scheduleThrow(0, new IOException("first"));
    mockSink.scheduleThrow(1, new IOException("second"));
    Deflater deflater = new Deflater();
    deflater.setLevel(Deflater.NO_COMPRESSION);
    DeflaterSink deflaterSink = new DeflaterSink(mockSink, deflater);
    deflaterSink.write(new Buffer().writeUtf8(repeat('a', Segment.SIZE)), Segment.SIZE);
    try {
      deflaterSink.close();
      fail();
    } catch (IOException expected) {
      assertEquals("first", expected.getMessage());
    }
    mockSink.assertLogContains("close()");
  }

  /**
   * Uses streaming decompression to inflate {@code deflated}. The input must
   * either be finished or have a trailing sync flush.
   */
  private Buffer inflate(Buffer deflated) throws IOException {
    InputStream deflatedIn = deflated.inputStream();
    Inflater inflater = new Inflater();
    InputStream inflatedIn = new InflaterInputStream(deflatedIn, inflater);
    Buffer result = new Buffer();
    byte[] buffer = new byte[8192];
    while (!inflater.needsInput() || deflated.size() > 0 || deflatedIn.available() > 0) {
      int count = inflatedIn.read(buffer, 0, buffer.length);
      if (count != -1) {
        result.write(buffer, 0, count);
      }
    }
    return result;
  }
}
