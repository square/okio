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

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path as NioPath
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import okio.internal.DefaultSocket
import okio.internal.PipeSocket
import okio.internal.ResourceFileSystem
import okio.internal.SocketAsyncTimeout
import okio.internal.isAndroidGetsocknameError

/** Returns a sink that writes to `out`. */
fun OutputStream.sink(): Sink = OutputStreamSink(this, Timeout())

private class OutputStreamSink(
  private val out: OutputStream,
  private val timeout: Timeout,
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

private open class InputStreamSource(
  private val input: InputStream,
  private val timeout: Timeout,
) : Source {

  override fun read(sink: Buffer, byteCount: Long): Long {
    if (byteCount == 0L) return 0L
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
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

@JvmName("socket")
fun Socket.asOkioSocket(): okio.Socket = DefaultSocket(this)

/**
 * Returns an array of two symmetric sockets, _A_ (element 0) and _B_ (element 1) that are mutually
 * connected:
 *
 *  * Pipe AB connects _A_’s sink to _B_’s source.
 *  * Pipe BA connects _B_’s sink to _A_’s source.
 *
 * Each pipe uses a buffer to decouple source and sink. This buffer has a user-specified maximum
 * size. When a socket writer outruns its corresponding reader, the buffer fills up and eventually
 * writes to the sink will block until the reader has caught up. Symmetrically, if a reader outruns
 * its writer, reads block until there is data to be read.
 *
 * There is a buffer for Pipe AB and another for Pipe BA. The maximum amount of memory that could be
 * held by the two sockets together is `maxBufferSize * 2`.
 *
 * Limit the amount of time spent waiting for the other party by configuring [timeouts][Timeout] on
 * the source and the sink.
 *
 * When the sink is closed, source reads will continue to complete normally until the buffer is
 * exhausted. At that point reads will return -1, indicating the end of the stream. But if the
 * source is closed first, writes to the sink will immediately fail with an [IOException].
 *
 * Canceling either socket immediately fails all reads and writes on both sockets.
 */
fun inMemorySocketPair(maxBufferSize: Long): Array<okio.Socket> {
  val ab = Pipe(maxBufferSize)
  val ba = Pipe(maxBufferSize)
  return arrayOf(PipeSocket(ab, ba), PipeSocket(ba, ab))
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
fun File.source(): Source = InputStreamSource(inputStream(), Timeout.NONE)

/** Returns a sink that writes to `path`. */
@Throws(IOException::class)
fun NioPath.sink(vararg options: OpenOption): Sink =
  Files.newOutputStream(this, *options).sink()

/** Returns a source that reads from `path`. */
@Throws(IOException::class)
fun NioPath.source(vararg options: OpenOption): Source =
  Files.newInputStream(this, *options).source()

/**
 * Returns a sink that uses [cipher] to encrypt or decrypt [this].
 *
 * @throws IllegalArgumentException if [cipher] isn't a block cipher.
 */
fun Sink.cipherSink(cipher: Cipher): CipherSink = CipherSink(this.buffer(), cipher)

/**
 * Returns a source that uses [cipher] to encrypt or decrypt [this].
 *
 * @throws IllegalArgumentException if [cipher] isn't a block cipher.
 */
fun Source.cipherSource(cipher: Cipher): CipherSource = CipherSource(this.buffer(), cipher)

/**
 * Returns a sink that uses [mac] to hash [this].
 */
fun Sink.hashingSink(mac: Mac): HashingSink = HashingSink(this, mac)

/**
 * Returns a source that uses [mac] to hash [this].
 */
fun Source.hashingSource(mac: Mac): HashingSource = HashingSource(this, mac)

/**
 * Returns a sink that uses [digest] to hash [this].
 */
fun Sink.hashingSink(digest: MessageDigest): HashingSink = HashingSink(this, digest)

/**
 * Returns a source that uses [digest] to hash [this].
 */
fun Source.hashingSource(digest: MessageDigest): HashingSource = HashingSource(this, digest)

fun ClassLoader.asResourceFileSystem(): FileSystem = ResourceFileSystem(this, indexEagerly = true)
