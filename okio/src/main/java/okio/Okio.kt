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

import okio.OldOkio.BlackholeSink
import okio.OldOkio.InputStreamSource
import okio.OldOkio.OutputStreamSink
import okio.OldOkio.SocketAsyncTimeout
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path

/**
 * Returns a new source that buffers reads from `source`. The returned
 * source will perform bulk reads into its in-memory buffer. Use this wherever
 * you read a source to get an ergonomic and efficient access to data.
 */
fun Source.buffer(): BufferedSource = RealBufferedSource(this)

/**
 * Returns a new sink that buffers writes to `sink`. The returned sink
 * will batch writes to `sink`. Use this wherever you write to a sink to
 * get an ergonomic and efficient access to data.
 */
fun Sink.buffer(): BufferedSink = RealBufferedSink(this)

/** Returns a sink that writes to `out`. */
fun OutputStream.sink(): Sink = OutputStreamSink(this, Timeout())

/** Returns a source that reads from `in`. */
fun InputStream.source(): Source = InputStreamSource(this, Timeout())

/** Returns a sink that writes nowhere. */
@JvmName("blackhole")
fun blackholeSink(): Sink = BlackholeSink()

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
