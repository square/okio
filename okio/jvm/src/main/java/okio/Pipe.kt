/*
 * Copyright (C) 2016 Square, Inc.
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

import java.io.IOException

/**
 * A source and a sink that are attached. The sink's output is the source's input. Typically each
 * is accessed by its own thread: a producer thread writes data to the sink and a consumer thread
 * reads data from the source.
 *
 * This class uses a buffer to decouple source and sink. This buffer has a user-specified maximum
 * size. When a producer thread outruns its consumer the buffer fills up and eventually writes to
 * the sink will block until the consumer has caught up. Symmetrically, if a consumer outruns its
 * producer reads block until there is data to be read. Limits on the amount of time spent waiting
 * for the other party can be configured with [timeouts][Timeout] on the source and the
 * sink.
 *
 * When the sink is closed, source reads will continue to complete normally until the buffer has
 * been exhausted. At that point reads will return -1, indicating the end of the stream. But if the
 * source is closed first, writes to the sink will immediately fail with an [IOException].
 */
class Pipe(internal val maxBufferSize: Long) {
  internal val buffer = Buffer()
  internal var sinkClosed = false
  internal var sourceClosed = false

  init {
    require(maxBufferSize >= 1L) { "maxBufferSize < 1: $maxBufferSize" }
  }

  @get:JvmName("sink")
  val sink = object : Sink {
    private val timeout = Timeout()

    override fun write(source: Buffer, byteCount: Long) {
      var byteCount = byteCount
      synchronized(buffer) {
        check(!sinkClosed) { "closed" }

        while (byteCount > 0) {
          if (sourceClosed) throw IOException("source is closed")

          val bufferSpaceAvailable = maxBufferSize - buffer.size
          if (bufferSpaceAvailable == 0L) {
            timeout.waitUntilNotified(buffer) // Wait until the source drains the buffer.
            continue
          }

          val bytesToWrite = minOf(bufferSpaceAvailable, byteCount)
          buffer.write(source, bytesToWrite)
          byteCount -= bytesToWrite
          (buffer as Object).notifyAll() // Notify the source that it can resume reading.
        }
      }
    }

    override fun flush() {
      synchronized(buffer) {
        check(!sinkClosed) { "closed" }
        if (sourceClosed && buffer.size > 0L) throw IOException("source is closed")
      }
    }

    override fun close() {
      synchronized(buffer) {
        if (sinkClosed) return
        if (sourceClosed && buffer.size > 0L) throw IOException("source is closed")
        sinkClosed = true
        (buffer as Object).notifyAll() // Notify the source that no more bytes are coming.
      }
    }

    override fun timeout(): Timeout = timeout
  }

  @get:JvmName("source")
  val source = object : Source {
    private val timeout = Timeout()

    override fun read(sink: Buffer, byteCount: Long): Long {
      synchronized(buffer) {
        check(!sourceClosed) { "closed" }

        while (buffer.size == 0L) {
          if (sinkClosed) return -1L
          timeout.waitUntilNotified(buffer) // Wait until the sink fills the buffer.
        }

        val result = buffer.read(sink, byteCount)
        (buffer as Object).notifyAll() // Notify the sink that it can resume writing.
        return result
      }
    }

    override fun close() {
      synchronized(buffer) {
        sourceClosed = true
        (buffer as Object).notifyAll() // Notify the sink that no more bytes are desired.
      }
    }

    override fun timeout(): Timeout = timeout
  }

  @JvmName("-deprecated_sink")
  @Deprecated(
      message = "moved to val",
      replaceWith = ReplaceWith(expression = "sink"),
      level = DeprecationLevel.ERROR)
  fun sink() = sink

  @JvmName("-deprecated_source")
  @Deprecated(
      message = "moved to val",
      replaceWith = ReplaceWith(expression = "source"),
      level = DeprecationLevel.ERROR)
  fun source() = source
}
