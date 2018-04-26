/*
 * Copyright (C) 2018 Square, Inc.
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

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.StandardOpenOption;
import kotlin.text.Charsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

/** Test interop between our beloved Okio and java.nio. */
public final class NioTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test public void sourceIsOpen() throws Exception {
    BufferedSource source = Okio.buffer((Source) new Buffer());
    assertTrue(source.isOpen());
    source.close();
    assertFalse(source.isOpen());
  }

  @Test public void sinkIsOpen() throws Exception {
    BufferedSink sink = Okio.buffer((Sink) new Buffer());
    assertTrue(sink.isOpen());
    sink.close();
    assertFalse(sink.isOpen());
  }

  @Test public void writableChannelNioFile() throws Exception {
    File file = temporaryFolder.newFile();
    FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE);
    testWritableByteChannel(fileChannel);

    BufferedSource emitted = Okio.buffer(Okio.source(file));
    assertEquals("defghijklmnopqrstuvw", emitted.readUtf8());
    emitted.close();
  }

  @Test public void writableChannelBuffer() throws Exception {
    Buffer buffer = new Buffer();
    testWritableByteChannel(buffer);
    assertEquals("defghijklmnopqrstuvw", buffer.readUtf8());
  }

  @Test public void writableChannelBufferedSink() throws Exception {
    Buffer buffer = new Buffer();
    BufferedSink bufferedSink = Okio.buffer((Sink) buffer);
    testWritableByteChannel(bufferedSink);
    assertEquals("defghijklmnopqrstuvw", buffer.readUtf8());
  }

  @Test public void readableChannelNioFile() throws Exception {
    File file = temporaryFolder.newFile();

    BufferedSink initialData = Okio.buffer(Okio.sink(file));
    initialData.writeUtf8("abcdefghijklmnopqrstuvwxyz");
    initialData.close();

    FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
    testReadableByteChannel(fileChannel);
  }

  @Test public void readableChannelBuffer() throws Exception {
    Buffer buffer = new Buffer();
    buffer.writeUtf8("abcdefghijklmnopqrstuvwxyz");

    testReadableByteChannel(buffer);
  }

  @Test public void readableChannelBufferedSource() throws Exception {
    Buffer buffer = new Buffer();
    BufferedSource bufferedSource = Okio.buffer((Source) buffer);
    buffer.writeUtf8("abcdefghijklmnopqrstuvwxyz");

    testReadableByteChannel(bufferedSource);
  }

  /**
   * Does some basic writes to {@code channel}. We execute this against both Okio's channels and
   * also a standard implementation from the JDK to confirm that their behavior is consistent.
   */
  private void testWritableByteChannel(WritableByteChannel channel) throws Exception {
    assertTrue(channel.isOpen());

    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    byteBuffer.put("abcdefghijklmnopqrstuvwxyz".getBytes(Charsets.UTF_8));
    byteBuffer.flip();
    byteBuffer.position(3);
    byteBuffer.limit(23);

    int byteCount = channel.write(byteBuffer);
    assertEquals(20, byteCount);
    assertEquals(23, byteBuffer.position());
    assertEquals(23, byteBuffer.limit());

    channel.close();
    assertEquals(channel instanceof Buffer, channel.isOpen()); // Buffer.close() does nothing.
  }

  /**
   * Does some basic reads from {@code channel}. We execute this against both Okio's channels and
   * also a standard implementation from the JDK to confirm that their behavior is consistent.
   */
  private void testReadableByteChannel(ReadableByteChannel channel) throws Exception {
    assertTrue(channel.isOpen());

    ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
    byteBuffer.position(3);
    byteBuffer.limit(23);

    int byteCount = channel.read(byteBuffer);
    assertEquals(20, byteCount);
    assertEquals(23, byteBuffer.position());
    assertEquals(23, byteBuffer.limit());

    channel.close();
    assertEquals(channel instanceof Buffer, channel.isOpen()); // Buffer.close() does nothing.

    byteBuffer.flip();
    byteBuffer.position(3);
    byte[] data = new byte[byteBuffer.remaining()];
    byteBuffer.get(data);
    assertEquals("abcdefghijklmnopqrst", new String(data, Charsets.UTF_8));
  }
}
