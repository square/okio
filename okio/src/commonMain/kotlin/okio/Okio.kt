/*
 * Copyright (C) 2019 Square, Inc.
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

import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

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

/** Returns a sink that writes nowhere. */
@JvmName("blackhole")
fun blackholeSink(): Sink = BlackholeSink()

private class BlackholeSink : Sink {
  override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)
  override fun flush() {}
  override fun timeout() = Timeout.NONE
  override fun close() {}
}
