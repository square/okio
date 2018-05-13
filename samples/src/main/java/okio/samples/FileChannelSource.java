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
import java.nio.channels.WritableByteChannel;
import okio.Buffer;
import okio.Source;
import okio.Timeout;

/**
 * Special Source for a FileChannel to take advantage of the
 * {@link FileChannel#transferTo(long, long, WritableByteChannel) transfer} method available.
 */
final class FileChannelSource implements Source {
  private final FileChannel channel;
  private final Timeout timeout;

  private long position;

  FileChannelSource(FileChannel channel, Timeout timeout) throws IOException {
    this.channel = channel;
    this.timeout = timeout;

    this.position = channel.position();
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    if (!channel.isOpen()) throw new IllegalStateException("closed");
    if (position == channel.size()) return -1L;

    long read = channel.transferTo(position, byteCount, sink);
    position += read;
    return read;
  }

  @Override public Timeout timeout() {
    return timeout;
  }

  @Override public void close() throws IOException {
    channel.close();
  }
}
