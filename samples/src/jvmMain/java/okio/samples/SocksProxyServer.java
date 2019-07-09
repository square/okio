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
package okio.samples;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import okio.Sink;
import okio.Source;

/**
 * A partial implementation of SOCKS Protocol Version 5.
 * See <a href="https://www.ietf.org/rfc/rfc1928.txt">RFC 1928</a>.
 */
public final class SocksProxyServer {
  private static final int VERSION_5 = 5;
  private static final int METHOD_NO_AUTHENTICATION_REQUIRED = 0;
  private static final int ADDRESS_TYPE_IPV4 = 1;
  private static final int ADDRESS_TYPE_DOMAIN_NAME = 3;
  private static final int COMMAND_CONNECT = 1;
  private static final int REPLY_SUCCEEDED = 0;

  private final ExecutorService executor = Executors.newCachedThreadPool();
  private ServerSocket serverSocket;
  private final Set<Socket> openSockets =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  public void start() throws IOException {
    serverSocket = new ServerSocket(0);
    executor.execute(this::acceptSockets);
  }

  public void shutdown() throws IOException {
    serverSocket.close();
    executor.shutdown();
  }

  public Proxy proxy() {
    return new Proxy(Proxy.Type.SOCKS,
        InetSocketAddress.createUnresolved("localhost", serverSocket.getLocalPort()));
  }

  private void acceptSockets() {
    try {
      while (true) {
        final Socket from = serverSocket.accept();
        openSockets.add(from);
        executor.execute(() -> handleSocket(from));
      }
    } catch (IOException e) {
      System.out.println("shutting down: " + e);
    } finally {
      for (Socket socket : openSockets) {
        closeQuietly(socket);
      }
    }
  }

  private void handleSocket(final Socket fromSocket) {
    try {
      final BufferedSource fromSource = Okio.buffer(Okio.source(fromSocket));
      final BufferedSink fromSink = Okio.buffer(Okio.sink(fromSocket));

      // Read the hello.
      int socksVersion = fromSource.readByte() & 0xff;
      if (socksVersion != VERSION_5) throw new ProtocolException();
      int methodCount = fromSource.readByte() & 0xff;
      boolean foundSupportedMethod = false;
      for (int i = 0; i < methodCount; i++) {
        int method = fromSource.readByte() & 0xff;
        foundSupportedMethod |= method == METHOD_NO_AUTHENTICATION_REQUIRED;
      }
      if (!foundSupportedMethod) throw new ProtocolException();

      // Respond to hello.
      fromSink.writeByte(VERSION_5)
          .writeByte(METHOD_NO_AUTHENTICATION_REQUIRED)
          .emit();

      // Read a command.
      int version = fromSource.readByte() & 0xff;
      int command = fromSource.readByte() & 0xff;
      int reserved = fromSource.readByte() & 0xff;
      if (version != VERSION_5 || command != COMMAND_CONNECT || reserved != 0) {
        throw new ProtocolException();
      }

      // Read an address.
      int addressType = fromSource.readByte() & 0xff;
      InetAddress inetAddress;
      if (addressType == ADDRESS_TYPE_IPV4) {
        inetAddress = InetAddress.getByAddress(fromSource.readByteArray(4L));
      } else if (addressType == ADDRESS_TYPE_DOMAIN_NAME){
        int domainNameLength = fromSource.readByte() & 0xff;
        inetAddress = InetAddress.getByName(fromSource.readUtf8(domainNameLength));
      } else {
        throw new ProtocolException();
      }
      int port = fromSource.readShort() & 0xffff;

      // Connect to the caller's specified host.
      final Socket toSocket = new Socket(inetAddress, port);
      openSockets.add(toSocket);
      byte[] localAddress = toSocket.getLocalAddress().getAddress();
      if (localAddress.length != 4) throw new ProtocolException();

      // Write the reply.
      fromSink.writeByte(VERSION_5)
          .writeByte(REPLY_SUCCEEDED)
          .writeByte(0)
          .writeByte(ADDRESS_TYPE_IPV4)
          .write(localAddress)
          .writeShort(toSocket.getLocalPort())
          .emit();

      // Connect sources to sinks in both directions.
      final Sink toSink = Okio.sink(toSocket);
      executor.execute(() -> transfer(fromSocket, fromSource, toSink));
      final Source toSource = Okio.source(toSocket);
      executor.execute(() -> transfer(toSocket, toSource, fromSink));
    } catch (IOException e) {
      closeQuietly(fromSocket);
      openSockets.remove(fromSocket);
      System.out.println("connect failed for " + fromSocket + ": " + e);
    }
  }

  /**
   * Read data from {@code source} and write it to {@code sink}. This doesn't use {@link
   * BufferedSink#writeAll} because that method doesn't flush aggressively and we need that.
   */
  private void transfer(Socket sourceSocket, Source source, Sink sink) {
    try {
      Buffer buffer = new Buffer();
      for (long byteCount; (byteCount = source.read(buffer, 8192L)) != -1; ) {
        sink.write(buffer, byteCount);
        sink.flush();
      }
    } catch (IOException e) {
      System.out.println("transfer failed from " + sourceSocket + ": " + e);
    } finally {
      closeQuietly(sink);
      closeQuietly(source);
      closeQuietly(sourceSocket);
      openSockets.remove(sourceSocket);
    }
  }

  private void closeQuietly(Closeable c) {
    try {
      c.close();
    } catch (IOException ignored) {
    }
  }

  public static void main(String[] args) throws IOException {
    SocksProxyServer proxyServer = new SocksProxyServer();
    proxyServer.start();

    URL url = new URL("https://publicobject.com/helloworld.txt");
    URLConnection connection = url.openConnection(proxyServer.proxy());
    try (BufferedSource source = Okio.buffer(Okio.source(connection.getInputStream()))) {
      for (String line; (line = source.readUtf8Line()) != null; ) {
        System.out.println(line);
      }
    }

    proxyServer.shutdown();
  }
}
