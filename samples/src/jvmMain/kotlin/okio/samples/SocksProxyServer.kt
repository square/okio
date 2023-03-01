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
package okio.samples

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProtocolException
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import okio.Buffer
import okio.BufferedSink
import okio.Sink
import okio.Source
import okio.buffer
import okio.sink
import okio.source

private const val VERSION_5 = 5
private const val METHOD_NO_AUTHENTICATION_REQUIRED = 0
private const val ADDRESS_TYPE_IPV4 = 1
private const val ADDRESS_TYPE_DOMAIN_NAME = 3
private const val COMMAND_CONNECT = 1
private const val REPLY_SUCCEEDED = 0

/**
 * A partial implementation of SOCKS Protocol Version 5.
 * See [RFC 1928](https://www.ietf.org/rfc/rfc1928.txt).
 */
class KotlinSocksProxyServer {
  private val executor = Executors.newCachedThreadPool()
  private lateinit var serverSocket: ServerSocket
  private val openSockets: MutableSet<Socket> = Collections.newSetFromMap(ConcurrentHashMap())

  @Throws(IOException::class)
  fun start() {
    serverSocket = ServerSocket(0)
    executor.execute { acceptSockets() }
  }

  @Throws(IOException::class)
  fun shutdown() {
    serverSocket.close()
    executor.shutdown()
  }

  fun proxy(): Proxy = Proxy(
    Proxy.Type.SOCKS,
    InetSocketAddress.createUnresolved("localhost", serverSocket.localPort),
  )

  private fun acceptSockets() {
    try {
      while (true) {
        val from = serverSocket.accept()
        openSockets.add(from)
        executor.execute { handleSocket(from) }
      }
    } catch (e: IOException) {
      println("shutting down: $e")
    } finally {
      for (socket in openSockets) {
        socket.close()
      }
    }
  }

  private fun handleSocket(fromSocket: Socket) {
    try {
      val fromSource = fromSocket.source().buffer()
      val fromSink = fromSocket.sink().buffer()

      // Read the hello.
      val socksVersion = fromSource.readByte().toInt() and 0xff
      if (socksVersion != VERSION_5) throw ProtocolException()
      val methodCount = fromSource.readByte().toInt() and 0xff
      var foundSupportedMethod = false
      for (i in 0 until methodCount) {
        val method: Int = fromSource.readByte().toInt() and 0xff
        foundSupportedMethod = foundSupportedMethod or (method == METHOD_NO_AUTHENTICATION_REQUIRED)
      }
      if (!foundSupportedMethod) throw ProtocolException()

      // Respond to hello.
      fromSink.writeByte(VERSION_5)
        .writeByte(METHOD_NO_AUTHENTICATION_REQUIRED)
        .emit()

      // Read a command.
      val version = fromSource.readByte().toInt() and 0xff
      val command = fromSource.readByte().toInt() and 0xff
      val reserved = fromSource.readByte().toInt() and 0xff
      if (version != VERSION_5 || command != COMMAND_CONNECT || reserved != 0) {
        throw ProtocolException()
      }

      // Read an address.
      val addressType = fromSource.readByte().toInt() and 0xff
      val inetAddress = when (addressType) {
        ADDRESS_TYPE_IPV4 -> InetAddress.getByAddress(fromSource.readByteArray(4L))
        ADDRESS_TYPE_DOMAIN_NAME -> {
          val domainNameLength: Int = fromSource.readByte().toInt() and 0xff
          InetAddress.getByName(fromSource.readUtf8(domainNameLength.toLong()))
        }
        else -> throw ProtocolException()
      }
      val port = fromSource.readShort().toInt() and 0xffff

      // Connect to the caller's specified host.
      val toSocket = Socket(inetAddress, port)
      openSockets.add(toSocket)
      val localAddress = toSocket.localAddress.address
      if (localAddress.size != 4) throw ProtocolException()

      // Write the reply.
      fromSink.writeByte(VERSION_5)
        .writeByte(REPLY_SUCCEEDED)
        .writeByte(0)
        .writeByte(ADDRESS_TYPE_IPV4)
        .write(localAddress)
        .writeShort(toSocket.localPort)
        .emit()

      // Connect sources to sinks in both directions.
      val toSink = toSocket.sink()
      executor.execute { transfer(fromSocket, fromSource, toSink) }
      val toSource = toSocket.source()
      executor.execute { transfer(toSocket, toSource, fromSink) }
    } catch (e: IOException) {
      fromSocket.close()
      openSockets.remove(fromSocket)
      println("connect failed for $fromSocket: $e")
    }
  }

  /**
   * Read data from `source` and write it to `sink`. This doesn't use [BufferedSink.writeAll]
   * because that method doesn't flush aggressively and we need that.
   */
  private fun transfer(sourceSocket: Socket, source: Source, sink: Sink) {
    try {
      val buffer = Buffer()
      var byteCount: Long
      while (source.read(buffer, 8192L).also { byteCount = it } != -1L) {
        sink.write(buffer, byteCount)
        sink.flush()
      }
    } catch (e: IOException) {
      println("transfer failed from $sourceSocket: $e")
    } finally {
      sink.close()
      source.close()
      sourceSocket.close()
      openSockets.remove(sourceSocket)
    }
  }
}

fun main() {
  val proxyServer = KotlinSocksProxyServer()
  proxyServer.start()

  val url = URL("https://publicobject.com/helloworld.txt")
  val connection = url.openConnection(proxyServer.proxy())
  connection.getInputStream().source().buffer().use { source ->
    generateSequence { source.readUtf8Line() }
      .forEach(::println)
  }

  proxyServer.shutdown()
}
