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
@file:JvmMultifileClass
@file:JvmName("Okio")

package okio

import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

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
    if (byteCount == 0L) return 0
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    try {
      timeout.throwIfReached()
      val tail = sink.writableSegment(1)
      val maxToCopy = minOf(byteCount, Segment.SIZE - tail.limit).toInt()
      val bytesRead = input.read(tail.data, tail.limit, maxToCopy)
      if (bytesRead == -1) {
        if (tail.pos == tail.limit) {
          // We allocated a tail segment, but didn't end up needing it. Recycle!
          sink.head = tail.pop()
          SegmentPool.recycle(tail)
        }
        return -1
      }
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

/**
 * Returns a sink that writes to `socket`. Prefer this over [sink]
 * because this method honors timeouts. When the socket
 * write times out, the socket is asynchronously closed by a watchdog thread.
 */
@Throws(IOException::class)
fun Socket.sink(): Sink {
  val timeout = SocketAsyncTimeout(this)
  val sink = OutputStreamSink(getOutputStream(), timeout)
  return timeout.sink(sink)
}

/**
 * Returns a source that reads from `socket`. Prefer this over [source]
 * because this method honors timeouts. When the socket
 * read times out, the socket is asynchronously closed by a watchdog thread.
 */
@Throws(IOException::class)
fun Socket.source(): Source {
  val timeout = SocketAsyncTimeout(this)
  val source = InputStreamSource(getInputStream(), timeout)
  return timeout.source(source)
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
