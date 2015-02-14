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
import org.junit.Test;

import static okio.TestUtil.repeat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GzipSinkTest {
  @Test public void gzipGunzip() throws Exception {
    Buffer data = new Buffer();
    String original = "It's a UNIX system! I know this!";
    data.writeUtf8(original);
    Buffer sink = new Buffer();
    GzipSink gzipSink = new GzipSink(sink);
    gzipSink.write(data, data.size());
    gzipSink.close();
    Buffer inflated = gunzip(sink);
    assertEquals(original, inflated.readUtf8());
  }

  @Test public void closeWithExceptionWhenWritingAndClosing() throws IOException {
    MockSink mockSink = new MockSink();
    mockSink.scheduleThrow(0, new IOException("first"));
    mockSink.scheduleThrow(1, new IOException("second"));
    GzipSink gzipSink = new GzipSink(mockSink);
    gzipSink.write(new Buffer().writeUtf8(repeat('a', Segment.SIZE)), Segment.SIZE);
    try {
      gzipSink.close();
      fail();
    } catch (IOException expected) {
      assertEquals("first", expected.getMessage());
    }
    mockSink.assertLogContains("close()");
  }

  private Buffer gunzip(Buffer gzipped) throws IOException {
    Buffer result = new Buffer();
    GzipSource source = new GzipSource(gzipped);
    while (source.read(result, Integer.MAX_VALUE) != -1) {
    }
    return result;
  }
}
