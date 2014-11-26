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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SocketTimeoutTest {

  // The size of the socket buffers to use. Less than half the data transferred during tests to
  // ensure send and receive buffers are flooded and any necessary blocking behavior takes place.
  private static final int SOCKET_BUFFER_SIZE = 256 * 1024;
  private static final int ONE_MB = 1024 * 1024;

  @Test public void readWithoutTimeout() throws Exception {
    Socket socket = socket(ONE_MB, 0);
    BufferedSource source = Okio.buffer(Okio.source(socket));
    source.timeout().timeout(5000, TimeUnit.MILLISECONDS);
    source.require(ONE_MB);
    socket.close();
  }

  @Test public void readWithTimeout() throws Exception {
    Socket socket = socket(0, 0);
    BufferedSource source = Okio.buffer(Okio.source(socket));
    source.timeout().timeout(250, TimeUnit.MILLISECONDS);
    try {
      source.require(ONE_MB);
      fail();
    } catch (InterruptedIOException expected) {
    }
    socket.close();
  }

  @Test public void writeWithoutTimeout() throws Exception {
    Socket socket = socket(0, ONE_MB);
    Sink sink = Okio.buffer(Okio.sink(socket));
    sink.timeout().timeout(500, TimeUnit.MILLISECONDS);
    byte[] data = new byte[ONE_MB];
    sink.write(new Buffer().write(data), data.length);
    sink.flush();
    socket.close();
  }

  @Test public void writeWithTimeout() throws Exception {
    Socket socket = socket(0, 0);
    Sink sink = Okio.sink(socket);
    sink.timeout().timeout(500, TimeUnit.MILLISECONDS);
    byte[] data = new byte[ONE_MB];
    long start = System.nanoTime();
    try {
      sink.write(new Buffer().write(data), data.length);
      sink.flush();
      fail();
    } catch (InterruptedIOException expected) {
    }
    long elapsed = System.nanoTime() - start;
    socket.close();

    assertTrue("elapsed: " + elapsed, TimeUnit.NANOSECONDS.toMillis(elapsed) >= 500);
    assertTrue("elapsed: " + elapsed, TimeUnit.NANOSECONDS.toMillis(elapsed) <= 750);
  }

  /**
   * Returns a socket that can read {@code readableByteCount} incoming bytes and
   * will accept {@code writableByteCount} written bytes. The socket will idle
   * for 5 seconds when the required data has been read and written.
   */
  static Socket socket(final int readableByteCount, final int writableByteCount) throws IOException {
    final ServerSocket serverSocket = new ServerSocket(0);
    serverSocket.setReuseAddress(true);
    serverSocket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);

    Thread peer = new Thread("peer") {
      @Override public void run() {
        Socket socket = null;
        try {
          socket = serverSocket.accept();
          socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
          writeFully(socket.getOutputStream(), readableByteCount);
          readFully(socket.getInputStream(), writableByteCount);
          Thread.sleep(5000); // Sleep 5 seconds so the peer can close the connection.
        } catch (Exception ignored) {
        } finally {
          try {
            if (socket != null) socket.close();
          } catch (IOException ignored) {
          }
        }
      }
    };
    peer.start();

    Socket socket = new Socket(serverSocket.getInetAddress(), serverSocket.getLocalPort());
    socket.setReceiveBufferSize(SOCKET_BUFFER_SIZE);
    socket.setSendBufferSize(SOCKET_BUFFER_SIZE);
    return socket;
  }

  private static void writeFully(OutputStream out, int byteCount) throws IOException {
    out.write(new byte[byteCount]);
    out.flush();
  }

  private static byte[] readFully(InputStream in, int byteCount) throws IOException {
    int count = 0;
    byte[] result = new byte[byteCount];
    while (count < byteCount) {
      int read = in.read(result, count, result.length - count);
      if (read == -1) throw new EOFException();
      count += read;
    }
    return result;
  }
}
