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

@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package okio

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.file.OpenOption
import java.nio.file.Path

/**
 * Returns a [BufferedSource] from this [Source].
 *
 * Note: Do not invoke this method multiple times or you will lose data! The returned instance
 * holds buffered data that will be lost if unconsumed.
 *
 * @see Okio.buffer
 */
inline fun Source.buffer(): BufferedSource = Okio.buffer(this)

/**
 * Returns a [BufferedSink] from this [Sink].
 *
 * Note: Do not invoke this method multiple times without first calling [BufferedSink.emit] or
 * [BufferedSink.flush] or you will lose data! The returned instance holds buffered data that
 * will be lost if not forced to this underlying [Sink].
 *
 * @see Okio.buffer
 */
inline fun Sink.buffer(): BufferedSink = Okio.buffer(this)

/**
 * Returns a [Sink] that writes to this [OutputStream].
 *
 * @see Okio.sink
 */
inline fun OutputStream.sink(): Sink = Okio.sink(this)

/**
 * Returns a [Sink] that writes to this [Socket].
 *
 * @see Okio.sink
 */
inline fun Socket.sink(): Sink = Okio.sink(this)

/**
 * Returns a [Source] that reads from this [InputStream].
 *
 * @see Okio.source
 */
inline fun InputStream.source(): Source = Okio.source(this)

/**
 * Returns a [Source] that reads from this [Socket].
 *
 * @see Okio.source
 */
inline fun Socket.source(): Source = Okio.source(this)

/**
 * Returns a [Sink] that writes to this [File].
 *
 * @param append Whet
 * @see Okio.sink
 * @see Okio.appendingSink
 */
inline fun File.sink(append: Boolean = false): Sink = if (append) {
  Okio.appendingSink(this)
} else {
  Okio.sink(this)
}

/**
 * Returns a [Source] that writers to this [File].
 *
 * @see Okio.source
 */
inline fun File.source(): Source = Okio.source(this)

/**
 * Returns a [Sink] that writes to this [Path].
 *
 * @see Okio.sink
 */
inline fun Path.sink(vararg openOption: OpenOption): Sink = Okio.sink(this, *openOption)

/**
 * Returns a [Source] that reads from this [Path].
 *
 * @see Okio.source
 */
inline fun Path.source(vararg openOption: OpenOption): Source = Okio.source(this, *openOption)

/** Returns a [Sink] that writes nowhere. */
inline fun blackholeSink(): Sink = Okio.blackhole()
