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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import okio.Buffer;
import okio.Sink;
import okio.Timeout;

/**
 * Special Sink for a FileChannel to take advantage of the
 * {@link FileChannel#transferFrom(ReadableByteChannel, long, long) transfer} method available.
 */
final class FileChannelSink implements Sink {
  private final FileChannel channel;
  private final Timeout timeout;

  private long position;

  FileChannelSink(FileChannel channel, Timeout timeout) throws IOException {
    this.channel = channel;
    this.timeout = timeout;

    this.position = channel.position();
  }

  @Override public void write(Buffer source, long byteCount) throws IOException {
    if (!channel.isOpen()) throw new IllegalStateException("closed");
    if (byteCount == 0) return;

    long remaining = byteCount;
    while (remaining > 0) {
      long written = channel.transferFrom(source, position, remaining);
      position += written;
      remaining -= written;
    }
  }

  @Override public void flush() throws IOException {
    // Cannot alter meta data through this Sink
    channel.force(false);
  }

  @Override public Timeout timeout() {
    return timeout;
  }

  @Override public void close() throws IOException {
    channel.close();
  }
}
