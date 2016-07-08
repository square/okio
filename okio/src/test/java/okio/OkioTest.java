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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static okio.TestUtil.repeat;
import static okio.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class OkioTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test public void readWriteFile() throws Exception {
    File file = temporaryFolder.newFile();

    BufferedSink sink = Okio.buffer(Okio.sink(file));
    sink.writeUtf8("Hello, java.io file!");
    sink.close();
    assertTrue(file.exists());
    assertEquals(20, file.length());

    BufferedSource source = Okio.buffer(Okio.source(file));
    assertEquals("Hello, java.io file!", source.readUtf8());
    source.close();
  }

  @Test public void appendFile() throws Exception {
    File file = temporaryFolder.newFile();

    BufferedSink sink = Okio.buffer(Okio.appendingSink(file));
    sink.writeUtf8("Hello, ");
    sink.close();
    assertTrue(file.exists());
    assertEquals(7, file.length());

    sink = Okio.buffer(Okio.appendingSink(file));
    sink.writeUtf8("java.io file!");
    sink.close();
    assertEquals(20, file.length());

    BufferedSource source = Okio.buffer(Okio.source(file));
    assertEquals("Hello, java.io file!", source.readUtf8());
    source.close();
  }

  @Test public void readWritePath() throws Exception {
    Path path = temporaryFolder.newFile().toPath();

    BufferedSink sink = Okio.buffer(Okio.sink(path));
    sink.writeUtf8("Hello, java.nio file!");
    sink.close();
    assertTrue(Files.exists(path));
    assertEquals(21, Files.size(path));

    BufferedSource source = Okio.buffer(Okio.source(path));
    assertEquals("Hello, java.nio file!", source.readUtf8());
    source.close();
  }

  @Test public void sinkFromOutputStream() throws Exception {
    Buffer data = new Buffer();
    data.writeUtf8("a");
    data.writeUtf8(repeat('b', 9998));
    data.writeUtf8("c");

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Sink sink = Okio.sink(out);
    sink.write(data, 3);
    assertEquals("abb", out.toString("UTF-8"));
    sink.write(data, data.size());
    assertEquals("a" + repeat('b', 9998) + "c", out.toString("UTF-8"));
  }

  @Test public void sourceFromInputStream() throws Exception {
    InputStream in = new ByteArrayInputStream(
        ("a" + repeat('b', Segment.SIZE * 2) + "c").getBytes(UTF_8));

    // Source: ab...bc
    Source source = Okio.source(in);
    Buffer sink = new Buffer();

    // Source: b...bc. Sink: abb.
    assertEquals(3, source.read(sink, 3));
    assertEquals("abb", sink.readUtf8(3));

    // Source: b...bc. Sink: b...b.
    assertEquals(Segment.SIZE, source.read(sink, 20000));
    assertEquals(repeat('b', Segment.SIZE), sink.readUtf8());

    // Source: b...bc. Sink: b...bc.
    assertEquals(Segment.SIZE - 1, source.read(sink, 20000));
    assertEquals(repeat('b', Segment.SIZE - 2) + "c", sink.readUtf8());

    // Source and sink are empty.
    assertEquals(-1, source.read(sink, 1));
  }

  @Test public void sourceFromInputStreamBounds() throws Exception {
    Source source = Okio.source(new ByteArrayInputStream(new byte[100]));
    try {
      source.read(new Buffer(), -1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void bufferSinkThrowsOnNull() {
    try {
      Okio.buffer((Sink) null);
      fail();
    } catch (NullPointerException expected) {
      assertEquals("sink == null", expected.getMessage());
    }
  }

  @Test public void bufferSourceThrowsOnNull() {
    try {
      Okio.buffer((Source) null);
      fail();
    } catch (NullPointerException expected) {
      assertEquals("source == null", expected.getMessage());
    }
  }

  @Test public void blackhole() throws Exception {
    Buffer data = new Buffer();
    data.writeUtf8("blackhole");

    Sink blackhole = Okio.blackhole();
    blackhole.write(data, 5);

    assertEquals("hole", data.readUtf8());
  }
}
