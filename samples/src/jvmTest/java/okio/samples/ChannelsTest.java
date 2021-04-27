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
package okio.samples;

import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;
import okio.Buffer;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;
import okio.Timeout;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ChannelsTest {
  private static final String quote =
      "John, the kind of control you're attempting simply is... it's not "
          + "possible. If there is one thing the history of evolution has "
          + "taught us it's that life will not be contained. Life breaks "
          + "free, it expands to new territories and crashes through "
          + "barriers, painfully, maybe even dangerously, but, uh... well, "
          + "there it is.";

  private static final Set<StandardOpenOption> r = EnumSet.of(READ);
  private static final Set<StandardOpenOption> w = EnumSet.of(WRITE);
  private static final Set<StandardOpenOption> append = EnumSet.of(WRITE, APPEND);

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test public void testReadChannel() throws Exception {
    ReadableByteChannel channel = new Buffer().writeUtf8(quote);

    Buffer buffer = new Buffer();
    Source source = new ByteChannelSource(channel, Timeout.NONE);
    source.read(buffer, 75);

    assertThat(buffer.readUtf8())
        .isEqualTo("John, the kind of control you're attempting simply is... it's not possible.");
  }

  @Test public void testReadChannelFully() throws Exception {
    ReadableByteChannel channel = new Buffer().writeUtf8(quote);

    BufferedSource source = Okio.buffer(new ByteChannelSource(channel, Timeout.NONE));
    assertThat(source.readUtf8())
        .isEqualTo(quote);
  }

  @Test public void testWriteChannel() throws Exception {
    Buffer channel = new Buffer();

    Sink sink = new ByteChannelSink(channel, Timeout.NONE);
    sink.write(new Buffer().writeUtf8(quote), 75);

    assertThat(channel.readUtf8())
        .isEqualTo("John, the kind of control you're attempting simply is... it's not possible.");
  }

  @Test public void testReadWriteFile() throws Exception {
    java.nio.file.Path path = temporaryFolder.newFile().toPath();

    Sink sink = new FileChannelSink(FileChannel.open(path, w), Timeout.NONE);
    sink.write(new Buffer().writeUtf8(quote), 317);
    sink.close();
    assertTrue(Files.exists(path));
    assertEquals(quote.length(), Files.size(path));

    Buffer buffer = new Buffer();
    Source source = new FileChannelSource(FileChannel.open(path, r), Timeout.NONE);

    source.read(buffer, 44);
    assertThat(buffer.readUtf8())
        .isEqualTo("John, the kind of control you're attempting ");

    source.read(buffer, 31);
    assertThat(buffer.readUtf8())
        .isEqualTo("simply is... it's not possible.");
  }

  @Test public void testAppend() throws Exception {
    java.nio.file.Path path = temporaryFolder.newFile().toPath();

    Buffer buffer = new Buffer().writeUtf8(quote);
    Sink sink;
    BufferedSource source;

    sink = new FileChannelSink(FileChannel.open(path, w), Timeout.NONE);
    sink.write(buffer, 75);
    sink.close();
    assertTrue(Files.exists(path));
    assertEquals(75, Files.size(path));

    source = Okio.buffer(new FileChannelSource(FileChannel.open(path, r), Timeout.NONE));
    assertThat(source.readUtf8())
        .isEqualTo("John, the kind of control you're attempting simply is... it's not possible.");

    sink = new FileChannelSink(FileChannel.open(path, append), Timeout.NONE);
    sink.write(buffer, buffer.size());
    sink.close();
    assertTrue(Files.exists(path));
    assertEquals(quote.length(), Files.size(path));

    source = Okio.buffer(new FileChannelSource(FileChannel.open(path, r), Timeout.NONE));
    assertThat(source.readUtf8())
        .isEqualTo(quote);
  }
}
