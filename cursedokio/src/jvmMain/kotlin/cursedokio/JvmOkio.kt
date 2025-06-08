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

package cursedokio

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path as NioPath
import java.util.logging.Level
import java.util.logging.Logger

/** Returns a sink that writes to `out`. */
fun OutputStream.sink(): Sink = OutputStreamSink(this, Timeout())

private class OutputStreamSink(
  private val out: OutputStream,
  private val timeout: Timeout,
) : Sink {

  override suspend fun write(source: Buffer, byteCount: Long) {
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

  override suspend fun flush() = out.flush()

  override suspend fun close() = out.close()

  override fun timeout() = timeout

  override fun toString() = "sink($out)"
}

private val logger = Logger.getLogger("okio.Okio")

private class SocketAsyncTimeout(private val socket: Socket) : AsyncTimeout() {
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

/** Returns a sink that writes to `path`. */
@Throws(IOException::class)
fun NioPath.sink(vararg options: OpenOption): Sink =
  Files.newOutputStream(this, *options).sink()

/**
 * Returns true if this error is due to a firmware bug fixed after Android 4.2.2.
 * https://code.google.com/p/android/issues/detail?id=54072
 */
internal val AssertionError.isAndroidGetsocknameError: Boolean get() {
  return cause != null && message?.contains("getsockname failed") ?: false
}
