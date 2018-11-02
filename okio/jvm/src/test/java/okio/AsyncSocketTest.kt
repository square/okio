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
package okio

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.SelectionKey
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class AsyncSocketTest {
  @Test
  fun asyncSocket() {
    withSocketPair { socketA, socketB ->
      val socketASource = socketA.source().buffer()
      val socketASink = socketA.sink().buffer()

      val socketBSource = socketB.source().buffer()
      val socketBSink = socketB.sink().buffer()

      socketASink.writeUtf8("abc")
      socketASink.flushAsync()
      assertThat(socketBSource.requestAsync(3)).isTrue()
      assertThat(socketBSource.readUtf8(3)).isEqualTo("abc")

      socketBSink.writeUtf8("def")
      socketBSink.flushAsync()
      assertThat(socketASource.requestAsync(3)).isTrue()
      assertThat(socketASource.readUtf8(3)).isEqualTo("def")
    }
  }

  @Test
  fun readUntilEof() {
    withSocketPair { socketA, socketB ->
      val socketASink = socketA.sink().buffer()
      val socketBSource = socketB.source().buffer()

      socketASink.writeUtf8("abc")
      socketASink.closeAsync()

      assertThat(socketBSource.readUtf8()).isEqualTo("abc")
    }
  }

  @Test
  fun readFailsBecauseTheSocketIsAlreadyClosed() {
    withSocketPair { socketA, socketB ->
      val socketBSource = socketB.source().buffer()

      socketB.close()

      assertFailsWith<IOException> {
        socketBSource.readUtf8()
      }
    }
  }

  @Test
  fun writeFailsBecauseTheSocketIsAlreadyClosed() {
    withSocketPair { socketA, socketB ->
      val socketBSink = socketB.sink().buffer()

      socketB.close()

      assertFailsWith<IOException> {
        socketBSink.writeUtf8("abc")
        socketBSink.flushAsync()
      }
    }
  }

  @Test
  fun blockedReadFailsDueToAsyncClose() {
    withSocketPair { socketA, socketB ->
      val socketBSource = socketB.source().buffer()

      coroutineScope {
        val deferred1 = async {
          delay(500)
          socketB.close()
        }

        val deferred2 = async {
          assertFailsWith<IOException> {
            socketBSource.requestAsync(1)
          }
        }

        deferred1.await()
        deferred2.await()
      }
    }
  }

  @Test
  fun blockedWriteFailsDueToAsyncClose() {
    withSocketPair { socketA, socketB ->
      val socketBSink = socketB.sink().buffer()

      coroutineScope {
        val deferred1 = async {
          delay(500)
          socketB.close()
        }

        val deferred2 = async {
          assertFailsWith<IOException> {
            socketBSink.writeUtf8("abc") // TODO: this needs to be a lot of data
            socketBSink.flushAsync()
          }
        }

        deferred1.await()
        deferred2.await()
      }
    }
  }

  fun withSocketPair(block: suspend (Socket, Socket) -> Unit) {
    runBlocking {
      val serverSocketChannel = ServerSocketChannel.open()
      serverSocketChannel.use {
        val serverSocket = serverSocketChannel.socket()
        serverSocket.reuseAddress = true
        serverSocketChannel.bind(InetSocketAddress(0))
        serverSocketChannel.configureBlocking(false)
        val serverAddress = InetSocketAddress(serverSocket.inetAddress, serverSocket.localPort)

        val socketA = connectAsync(serverAddress)
        socketA.use {
          val socketB = serverSocketChannel.acceptAsync()
          socketB.use {
            block(socketA, socketB)
          }
        }
      }
    }
  }

  suspend fun connectAsync(address: InetSocketAddress): Socket {
    val channel = SocketChannel.open()
    channel.configureBlocking(false)
    channel.connect(address)
    return channel.selectAsync(SelectionKey.OP_CONNECT) {
      if (!channel.finishConnect()) throw IOException("connect failed")
      return@selectAsync channel.socket()
    }
  }

  suspend fun ServerSocketChannel.acceptAsync(): Socket {
    return selectAsync(SelectionKey.OP_ACCEPT) {
      val channel = accept()
      channel.configureBlocking(false)
      return@selectAsync channel.socket()
    }
  }

  // TODO(jwilson): figure out why Kotlin's built-in assertFailsWith doesn't like our coroutines.
  inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
    try {
      block()
    } catch (e: Exception) {
      if (e is T) return e
      throw e
    }
    throw AssertionError("expected exception not thrown")
  }
}
