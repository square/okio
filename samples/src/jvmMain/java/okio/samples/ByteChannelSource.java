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
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import okio.Buffer;
import okio.Source;
import okio.Timeout;

/**
 * Creates a Source around a ReadableByteChannel and efficiently reads data using an UnsafeCursor.
 *
 * <p>This is a basic example showing another use for the UnsafeCursor. Using the
 * {@link ByteBuffer#wrap(byte[], int, int) ByteBuffer.wrap()} along with access to Buffer segments,
 * a ReadableByteChannel can be given direct access to Buffer data without having to copy the data.
 */
final class ByteChannelSource implements Source {
  private final ReadableByteChannel channel;
  private final Timeout timeout;

  private final Buffer.UnsafeCursor cursor = new Buffer.UnsafeCursor();

  ByteChannelSource(ReadableByteChannel channel, Timeout timeout) {
    this.channel = channel;
    this.timeout = timeout;
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    if (!channel.isOpen()) throw new IllegalStateException("closed");

    try (Buffer.UnsafeCursor ignored = sink.readAndWriteUnsafe(cursor)) {
      timeout.throwIfReached();
      long oldSize = sink.size();
      int length = (int) Math.min(8192, byteCount);

      cursor.expandBuffer(length);
      int read = channel.read(ByteBuffer.wrap(cursor.data, cursor.start, length));
      if (read == -1) {
        cursor.resizeBuffer(oldSize);
        return -1;
      } else {
        cursor.resizeBuffer(oldSize + read);
        return read;
      }
    }
  }

  @Override public Timeout timeout() {
    return timeout;
  }

  @Override public void close() throws IOException {
    channel.close();
  }
}
