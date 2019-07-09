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
import java.nio.channels.WritableByteChannel;
import okio.Buffer;
import okio.Sink;
import okio.Timeout;

/**
 * Creates a Sink around a WritableByteChannel and efficiently writes data using an UnsafeCursor.
 *
 * <p>This is a basic example showing another use for the UnsafeCursor. Using the
 * {@link ByteBuffer#wrap(byte[], int, int) ByteBuffer.wrap()} along with access to Buffer segments,
 * a WritableByteChannel can be given direct access to Buffer data without having to copy the data.
 */
final class ByteChannelSink implements Sink {
  private final WritableByteChannel channel;
  private final Timeout timeout;

  private final Buffer.UnsafeCursor cursor = new Buffer.UnsafeCursor();

  ByteChannelSink(WritableByteChannel channel, Timeout timeout) {
    this.channel = channel;
    this.timeout = timeout;
  }

  @Override public void write(Buffer source, long byteCount) throws IOException {
    if (!channel.isOpen()) throw new IllegalStateException("closed");
    if (byteCount == 0) return;

    long remaining = byteCount;
    while (remaining > 0) {
      timeout.throwIfReached();

      try (Buffer.UnsafeCursor ignored = source.readUnsafe(cursor)) {
        cursor.seek(0);
        int length = (int) Math.min(cursor.end - cursor.start, remaining);
        int written = channel.write(ByteBuffer.wrap(cursor.data, cursor.start, length));
        remaining -= written;
        source.skip(written);
      }
    }
  }

  @Override public void flush() {}

  @Override public Timeout timeout() {
    return timeout;
  }

  @Override public void close() throws IOException {
    channel.close();
  }
}
