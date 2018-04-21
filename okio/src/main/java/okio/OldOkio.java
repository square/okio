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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

import static okio.Util.checkOffsetAndCount;

final class OldOkio {
  static final Logger logger = Logger.getLogger(Okio.class.getName());

  private OldOkio() {
  }

  /**
   * Returns true if {@code e} is due to a firmware bug fixed after Android 4.2.2.
   * https://code.google.com/p/android/issues/detail?id=54072
   */
  static boolean isAndroidGetsocknameError(AssertionError e) {
    return e.getCause() != null && e.getMessage() != null
        && e.getMessage().contains("getsockname failed");
  }

  static class OutputStreamSink implements Sink {
    private final Timeout timeout;
    private final OutputStream out;

    OutputStreamSink(OutputStream out, Timeout timeout) {
      this.timeout = timeout;
      this.out = out;
    }

    @Override public void write(Buffer source, long byteCount) throws IOException {
      checkOffsetAndCount(source.size, 0, byteCount);
      while (byteCount > 0) {
        timeout.throwIfReached();
        Segment head = source.head;
        int toCopy = (int) Math.min(byteCount, head.limit - head.pos);
        out.write(head.data, head.pos, toCopy);

        head.pos += toCopy;
        byteCount -= toCopy;
        source.size -= toCopy;

        if (head.pos == head.limit) {
          source.head = head.pop();
          SegmentPool.recycle(head);
        }
      }
    }

    @Override public void flush() throws IOException {
      out.flush();
    }

    @Override public void close() throws IOException {
      out.close();
    }

    @Override public Timeout timeout() {
      return timeout;
    }

    @Override public String toString() {
      return "sink(" + out + ")";
    }
  }

  static class InputStreamSource implements Source {
    private final Timeout timeout;
    private final InputStream in;

    InputStreamSource(InputStream in, Timeout timeout) {
      this.timeout = timeout;
      this.in = in;
    }

    @Override public long read(Buffer sink, long byteCount) throws IOException {
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      if (byteCount == 0) return 0;
      try {
        timeout.throwIfReached();
        Segment tail = sink.writableSegment(1);
        int maxToCopy = (int) Math.min(byteCount, Segment.SIZE - tail.limit);
        int bytesRead = in.read(tail.data, tail.limit, maxToCopy);
        if (bytesRead == -1) return -1;
        tail.limit += bytesRead;
        sink.size += bytesRead;
        return bytesRead;
      } catch (AssertionError e) {
        if (isAndroidGetsocknameError(e)) throw new IOException(e);
        throw e;
      }
    }

    @Override public void close() throws IOException {
      in.close();
    }

    @Override public Timeout timeout() {
      return timeout;
    }

    @Override public String toString() {
      return "source(" + in + ")";
    }
  }

  static class BlackholeSink implements Sink {
    @Override public void write(Buffer source, long byteCount) throws IOException {
      source.skip(byteCount);
    }

    @Override public void flush() {
    }

    @Override public Timeout timeout() {
      return Timeout.NONE;
    }

    @Override public void close() throws IOException {
    }
  }

  static class SocketAsyncTimeout extends AsyncTimeout {
    private final Socket socket;

    SocketAsyncTimeout(Socket socket) {
      this.socket = socket;
    }

    @Override protected IOException newTimeoutException(@Nullable IOException cause) {
      InterruptedIOException ioe = new SocketTimeoutException("timeout");
      if (cause != null) {
        ioe.initCause(cause);
      }
      return ioe;
    }

    @Override protected void timedOut() {
      try {
        socket.close();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Failed to close timed out socket " + socket, e);
      } catch (AssertionError e) {
        if (isAndroidGetsocknameError(e)) {
          // Catch this exception due to a Firmware issue up to android 4.2.2
          // https://code.google.com/p/android/issues/detail?id=54072
          logger.log(Level.WARNING, "Failed to close timed out socket " + socket, e);
        } else {
          throw e;
        }
      }
    }
  }
}
