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

/** Essential APIs for working with Okio. */
@file:JvmName("Okio")

package okio

import kotlinx.coroutines.runBlocking
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Returns a new source that buffers reads from `source`. The returned source will perform bulk
 * reads into its in-memory buffer. Use this wherever you read a source to get an ergonomic and
 * efficient access to data.
 */
fun Source.buffer(): BufferedSource = RealBufferedSource(this)

/**
 * Returns a new sink that buffers writes to `sink`. The returned sink will batch writes to `sink`.
 * Use this wherever you write to a sink to get an ergonomic and efficient access to data.
 */
fun Sink.buffer(): BufferedSink = RealBufferedSink(this)

/** Returns a sink that writes to `out`. */
fun OutputStream.sink(): Sink = OutputStreamSink(this, Timeout())

private class OutputStreamSink(
  private val out: OutputStream,
  private val timeout: Timeout
) : Sink {

  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)
    var remaining = byteCount
    while (remaining > 0) {
      timeout.throwIfReached()
      val head = source.head!!
      val toCopy = minOf(remaining, head.limit - head.pos).toInt()
      out.write(head.data, head.pos, toCopy)

      head.pos += toCopy
      remaining -= toCopy
      source.size -= toCopy

      if (head.pos == head.limit) {
        source.head = head.pop()
        SegmentPool.recycle(head)
      }
    }
  }

  override fun flush() = out.flush()

  override fun close() = out.close()

  override fun timeout() = timeout

  override fun toString() = "sink($out)"
}

/** Returns a source that reads from `in`. */
fun InputStream.source(): Source = InputStreamSource(this, Timeout())

private class InputStreamSource(
  private val input: InputStream,
  private val timeout: Timeout
) : Source {

  override fun read(sink: Buffer, byteCount: Long): Long {
    if (byteCount == 0L) return 0L
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    try {
      timeout.throwIfReached()
      val tail = sink.writableSegment(1)
      val maxToCopy = minOf(byteCount, Segment.SIZE - tail.limit).toInt()
      val bytesRead = input.read(tail.data, tail.limit, maxToCopy)
      if (bytesRead == -1) return -1L
      tail.limit += bytesRead
      sink.size += bytesRead
      return bytesRead.toLong()
    } catch (e: AssertionError) {
      if (e.isAndroidGetsocknameError) throw IOException(e)
      throw e
    }
  }

  override fun close() = input.close()

  override fun timeout() = timeout

  override fun toString() = "source($input)"
}

/** Returns a sink that writes nowhere. */
@JvmName("blackhole")
fun blackholeSink(): Sink = BlackholeSink()

private class BlackholeSink : Sink {
  override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)
  override suspend fun writeAsync(source: Buffer, byteCount: Long) = write(source, byteCount)
  override fun flush() {}
  override suspend fun flushAsync() {}
  override fun timeout() = Timeout.NONE
  override fun close() {}
  override suspend fun closeAsync() {}
}

/**
 * Returns a sink that writes to `socket`. Prefer this over [sink]
 * because this method honors timeouts. When the socket
 * write times out, the socket is asynchronously closed by a watchdog thread.
 */
@Throws(IOException::class)
fun Socket.sink(): Sink {
  val timeout = SocketAsyncTimeout(this)
  val sink = SelectableSocketSink(this, timeout)
  return sink // TODO(jwilson): restore timeouts.
}

private class SelectableSocketSource(
  socket: Socket,
  val timeout: SocketAsyncTimeout
) : Source {
  val channel: SocketChannel = socket.channel!!

  override fun read(sink: Buffer, byteCount: Long): Long {
    return runBlocking {
      readAsync(sink, byteCount)
    }
  }

  override suspend fun readAsync(sink: Buffer, byteCount: Long): Long {
    return channel.selectAsync(SelectionKey.OP_READ) {
      channel.read(sink, byteCount)
    }
  }

  override fun timeout(): Timeout = timeout

  override fun close() {
    channel.close() // TODO(jwilson): confirm nonblocking.
  }

  override suspend fun closeAsync() {
    channel.close() // TODO(jwilson): confirm nonblocking.
  }
}

private class SelectableSocketSink(
  socket: Socket,
  val timeout: SocketAsyncTimeout
) : Sink {
  val channel: SocketChannel = socket.channel!!

  override fun write(source: Buffer, byteCount: Long) {
    runBlocking {
      writeAsync(source, byteCount)
    }
  }

  override suspend fun writeAsync(source: Buffer, byteCount: Long) {
    var byteCount = byteCount
    while (byteCount > 0) {
      byteCount = channel.selectAsync(SelectionKey.OP_WRITE) {
        channel.write(source, byteCount)
      }
    }
  }

  override fun flush() {
    // Do nothing. No buffering beneath this layer.
  }

  override suspend fun flushAsync() {
    // Do nothing. No buffering beneath this layer.
  }

  override fun timeout(): Timeout = timeout

  override fun close() {
    channel.close() // TODO(jwilson): confirm nonblocking.
  }

  override suspend fun closeAsync() {
    channel.close() // TODO(jwilson): confirm nonblocking.
  }
}

/**
 * Writes up to `byteCount` bytes to this socket channel from `source`. Returns 0 if all requested
 * data was written; otherwise the number of bytes still to be written.
 */
private fun SocketChannel.write(source: Buffer, byteCount: Long): Long {
  checkOffsetAndCount(source.size, 0, byteCount)
  var remaining = byteCount
  while (remaining > 0) {
    val head = source.head!!
    val toCopy = minOf(byteCount, head.limit - head.pos).toInt()
    val bytesWritten = write(ByteBuffer.wrap(head.data, head.pos, toCopy))

    if (bytesWritten == 0) break

    head.pos += bytesWritten
    remaining -= bytesWritten
    source.size -= bytesWritten

    if (head.pos == head.limit) {
      source.head = head.pop()
      SegmentPool.recycle(head)
    }
  }
  return remaining
}

/**
 * Writes up to `byteCount` bytes from this socket channel to `sink`. Returns the number of bytes
 * that were read. Returns -1L if no bytes were read.
 */
private fun SocketChannel.read(sink: Buffer, byteCount: Long): Long {
  if (byteCount == 0L) return 0L
  require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
  try {
    val tail = sink.writableSegment(1)
    val maxToCopy = minOf(byteCount, Segment.SIZE - tail.limit).toInt()
    val bytesRead = read(ByteBuffer.wrap(tail.data, tail.limit, maxToCopy))
    if (bytesRead == -1) return -1L
    tail.limit += bytesRead
    sink.size += bytesRead
    return bytesRead.toLong()
  } catch (e: AssertionError) {
    if (e.isAndroidGetsocknameError) throw IOException(e)
    throw e
  }
}

/**
 * Returns a source that reads from `socket`. Prefer this over [source]
 * because this method honors timeouts. When the socket
 * read times out, the socket is asynchronously closed by a watchdog thread.
 */
@Throws(IOException::class)
fun Socket.source(): Source {
  val timeout = SocketAsyncTimeout(this)
  val source = SelectableSocketSource(this, timeout)
  return source // TODO(jwilson): restore timeouts.
}

private class SocketAsyncTimeout(private val socket: Socket) : AsyncTimeout() {
  private val logger = Logger.getLogger("okio.Okio")

  override fun newTimeoutException(cause: IOException?): IOException {
    val ioe = SocketTimeoutException("timeout")
    if (cause != null) {
      ioe.initCause(cause)
    }
    return ioe
  }

  override fun timedOut() {
    try {
      socket.close()
    } catch (e: Exception) {
      logger.log(Level.WARNING, "Failed to close timed out socket $socket", e)
    } catch (e: AssertionError) {
      if (e.isAndroidGetsocknameError) {
        // Catch this exception due to a Firmware issue up to android 4.2.2
        // https://code.google.com/p/android/issues/detail?id=54072
        logger.log(Level.WARNING, "Failed to close timed out socket $socket", e)
      } else {
        throw e
      }
    }
  }
}

/** Returns a sink that writes to `file`. */
@JvmOverloads
@Throws(FileNotFoundException::class)
fun File.sink(append: Boolean = false): Sink = FileOutputStream(this, append).sink()

/** Returns a sink that writes to `file`. */
@Throws(FileNotFoundException::class)
fun File.appendingSink(): Sink = FileOutputStream(this, true).sink()

/** Returns a source that reads from `file`. */
@Throws(FileNotFoundException::class)
fun File.source(): Source = inputStream().source()

/** Returns a source that reads from `path`. */
@Throws(IOException::class)
@IgnoreJRERequirement // Can only be invoked on Java 7+.
fun Path.sink(vararg options: OpenOption): Sink =
    Files.newOutputStream(this, *options).sink()

/** Returns a sink that writes to `path`. */
@Throws(IOException::class)
@IgnoreJRERequirement // Can only be invoked on Java 7+.
fun Path.source(vararg options: OpenOption): Source =
    Files.newInputStream(this, *options).source()

/**
 * Returns true if this error is due to a firmware bug fixed after Android 4.2.2.
 * https://code.google.com/p/android/issues/detail?id=54072
 */
internal val AssertionError.isAndroidGetsocknameError: Boolean get() {
  return cause != null && message?.contains("getsockname failed") ?: false
}
