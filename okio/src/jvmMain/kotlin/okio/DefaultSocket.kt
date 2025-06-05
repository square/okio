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

import java.net.Socket as JavaNetSocket
import java.util.concurrent.atomic.AtomicInteger
import okio.internal.SocketAsyncTimeout
import okio.internal.read
import okio.internal.setBitOrZero

internal class DefaultSocket(val socket: JavaNetSocket) : Socket {
  private var closeBits = AtomicInteger()

  override val source: Source = SocketSource()
  override val sink: Sink = SocketSink()

  override fun cancel() {
    socket.close()
  }

  override fun close() {
    var throwable: Throwable? = null

    try {
      sink.close()
    } catch (e: Throwable) {
      throwable = e
    }

    try {
      source.close()
    } catch (e: Throwable) {
      if (throwable != null) throwable.addSuppressed(e)
      else throwable = e
    }

    if (throwable != null) throw throwable
  }

  override fun toString() = socket.toString()

  inner class SocketSink : Sink {
    private val timeout = SocketAsyncTimeout(socket)

    override fun write(source: Buffer, byteCount: Long) {
      checkOffsetAndCount(source.size, 0, byteCount)
      var remaining = byteCount
      while (remaining > 0L) {
        timeout.throwIfReached()
        val head = source.head!!
        val toCopy = minOf(remaining, head.limit - head.pos).toInt()
        timeout.withTimeout {
          socket.outputStream.write(head.data, head.pos, toCopy)
        }

        head.pos += toCopy
        remaining -= toCopy
        source.size -= toCopy

        if (head.pos == head.limit) {
          source.head = head.pop()
          SegmentPool.recycle(head)
        }
      }
    }

    override fun flush() {
      timeout.withTimeout {
        socket.outputStream.flush()
      }
    }

    override fun close() {
      timeout.withTimeout {
        when (closeBits.setBitOrZero(SINK_CLOSED_BIT)) {
          // If setBitOrZero() returns 0, this sink is already closed.
          0 -> return

          // Release the socket if both streams are closed.
          ALL_CLOSED_BITS -> socket.close()

          // Close this stream only.
          else -> {
            if (socket.isClosed || socket.isOutputShutdown) return // Nothing to do.
            socket.outputStream.flush()
            socket.shutdownOutput()
          }
        }
      }
    }

    override fun timeout() = timeout

    override fun toString() = "sink($socket)"
  }

  inner class SocketSource : Source {
    private val timeout = SocketAsyncTimeout(socket)

    override fun read(sink: Buffer, byteCount: Long): Long {
      timeout.withTimeout {
        return socket.inputStream.read(sink, byteCount, timeout)
      }
    }

    override fun close() {
      timeout.withTimeout {
        when (closeBits.setBitOrZero(SOURCE_CLOSED_BIT)) {
          // If setBitOrZero() returns 0, this source is already closed.
          0 -> return

          // Release the socket if both streams are closed.
          ALL_CLOSED_BITS -> socket.close()

          // Close this stream only.
          else -> {
            if (socket.isClosed || socket.isInputShutdown) return // Nothing to do.
            socket.shutdownInput()
          }
        }
      }
    }

    override fun timeout() = timeout

    override fun toString() = "source($socket)"
  }
}

private const val SINK_CLOSED_BIT = 1
private const val SOURCE_CLOSED_BIT = 2
private const val ALL_CLOSED_BITS = SINK_CLOSED_BIT or SOURCE_CLOSED_BIT
