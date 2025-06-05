/*
 * Copyright (C) 2025 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingDeque
import javax.net.SocketFactory
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Before
import org.junit.Test

class SocketTest {
  private val server = LocalServer()

  @Before
  fun setUp() {
    server.start()
  }

  @After
  fun tearDown() {
    server.close()
  }

  @Test
  fun happyPath() {
    val javaNetSocket = SocketFactory.getDefault().createSocket()
    val okioSocket = javaNetSocket.asOkioSocket()

    okioSocket.use {
      javaNetSocket.connect(server.address)

      okioSocket.sink.buffer().use { it.writeUtf8("hello") }
      assertThat(server.receive()).isEqualTo("hello")

      server.send("world")
      val received = okioSocket.source.buffer().use { it.readUtf8Line() }
      assertThat(received).isEqualTo("world")
    }

    server.join()
  }

  @Test
  fun closeSinkThenCloseSourceWillCloseSocket() {
    val javaNetSocket = SocketFactory.getDefault().createSocket()
    val okioSocket = javaNetSocket.asOkioSocket()
    javaNetSocket.connect(server.address)

    assertThat(javaNetSocket.isClosed).isFalse()
    okioSocket.sink.close()
    assertThat(javaNetSocket.isClosed).isFalse()
    okioSocket.source.close()
    assertThat(javaNetSocket.isClosed).isTrue()
  }

  @Test
  fun closeSourceThenCloseSinkWillCloseSocket() {
    val javaNetSocket = SocketFactory.getDefault().createSocket()
    val okioSocket = javaNetSocket.asOkioSocket()
    javaNetSocket.connect(server.address)

    assertThat(javaNetSocket.isClosed).isFalse()
    okioSocket.source.close()
    assertThat(javaNetSocket.isClosed).isFalse()
    okioSocket.sink.close()
    assertThat(javaNetSocket.isClosed).isTrue()
  }

  @Test
  fun closeSourceIsIdempotent() {
    val javaNetSocket = SocketFactory.getDefault().createSocket()
    val okioSocket = javaNetSocket.asOkioSocket()
    javaNetSocket.connect(server.address)

    assertThat(javaNetSocket.isClosed).isFalse()
    okioSocket.source.close()
    okioSocket.source.close()
    assertThat(javaNetSocket.isClosed).isFalse()
    okioSocket.sink.close()
    assertThat(javaNetSocket.isClosed).isTrue()
  }

  @Test
  fun closeSinkIsIdempotent() {
    val javaNetSocket = SocketFactory.getDefault().createSocket()
    val okioSocket = javaNetSocket.asOkioSocket()
    javaNetSocket.connect(server.address)

    assertThat(javaNetSocket.isClosed).isFalse()
    okioSocket.sink.close()
    okioSocket.sink.close()
    assertThat(javaNetSocket.isClosed).isFalse()
    okioSocket.source.close()
    assertThat(javaNetSocket.isClosed).isTrue()
  }

  @Test
  fun socketStreamsAreAlreadyClosed() {
    val javaNetSocket = SocketFactory.getDefault().createSocket()
    javaNetSocket.connect(server.address)
    val okioSocket = javaNetSocket.asOkioSocket()
    val sink = okioSocket.sink
    val source = okioSocket.source
    javaNetSocket.shutdownOutput()
    javaNetSocket.shutdownInput()

    assertThat(javaNetSocket.isClosed).isFalse()
    sink.close()
    assertThat(javaNetSocket.isClosed).isFalse()
    source.close()
    assertThat(javaNetSocket.isClosed).isTrue()
  }

  /** Accepts a string from the client, writes a string back, and closes everything up. */
  private class LocalServer : Thread("LocalServer"), Closeable {
    private val inetAddress = InetAddress.getByName("localhost")
    private val serverSocket = ServerSocket(0, 50, inetAddress)
    private val receives = LinkedBlockingDeque<String>()
    private val sends = LinkedBlockingDeque<String>()

    val address: InetSocketAddress
      get() = InetSocketAddress(inetAddress, serverSocket.localPort)

    fun send(line: String) {
      sends.push("$line\n")
    }

    fun receive(): String {
      return receives.take()
    }

    override fun run() {
      serverSocket.use {
        serverSocket.reuseAddress = true
        processSocket(serverSocket.accept().asOkioSocket())
      }
    }

    private fun processSocket(socket: Socket) {
      thread(name = "$socket source reader") {
        socket.source.buffer().use {
          while (true) {
            val line = it.readUtf8Line() ?: break
            receives.push(line)
          }
          receives.push("<eof>")
        }
      }

      thread(name = "$socket sink writer") {
        socket.sink.buffer().use {
          while (true) {
            val line = sends.take()
            if (line == "<eof>") break
            it.writeUtf8("$line\n")
          }
          sends.push("<eof>>")
        }
      }
    }

    override fun close() {
      serverSocket.close()
      sends.push("<eof>")
    }
  }
}
