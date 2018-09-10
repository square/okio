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

@file:JvmName("-InflaterSourceExtensions")
@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package okio

import java.io.EOFException
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * A source that uses [DEFLATE](http://tools.ietf.org/html/rfc1951) to decompress data read from
 * another source.
 */
class InflaterSource
/**
 * This internal constructor shares a buffer with its trusted caller. In general we can't share a
 * `BufferedSource` because the inflater holds input bytes until they are inflated.
 */
internal constructor(private val source: BufferedSource, private val inflater: Inflater) : Source {

  /**
   * When we call Inflater.setInput(), the inflater keeps our byte array until it needs input again.
   * This tracks how many bytes the inflater is currently holding on to.
   */
  private var bufferBytesHeldByInflater = 0
  private var closed = false

  constructor(source: Source, inflater: Inflater) : this(source.buffer(), inflater)

  @Throws(IOException::class)
  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    if (byteCount == 0L) return 0

    while (true) {
      val sourceExhausted = refill()

      // Decompress the inflater's compressed data into the sink.
      try {
        val tail = sink.writableSegment(1)
        val toRead = minOf(byteCount, Segment.SIZE - tail.limit).toInt()
        val bytesInflated = inflater.inflate(tail.data, tail.limit, toRead)
        if (bytesInflated > 0) {
          tail.limit += bytesInflated
          sink.size += bytesInflated
          return bytesInflated.toLong()
        }
        if (inflater.finished() || inflater.needsDictionary()) {
          releaseInflatedBytes()
          if (tail.pos == tail.limit) {
            // We allocated a tail segment, but didn't end up needing it. Recycle!
            sink.head = tail.pop()
            SegmentPool.recycle(tail)
          }
          return -1L
        }
        if (sourceExhausted) throw EOFException("source exhausted prematurely")
      } catch (e: DataFormatException) {
        throw IOException(e)
      }
    }
  }

  /**
   * Refills the inflater with compressed data if it needs input. (And only if it needs input).
   * Returns true if the inflater required input but the source was exhausted.
   */
  @Throws(IOException::class)
  fun refill(): Boolean {
    if (!inflater.needsInput()) return false

    releaseInflatedBytes()
    check(inflater.remaining == 0) { "?" } // TODO: possible?

    // If there are compressed bytes in the source, assign them to the inflater.
    if (source.exhausted()) return true

    // Assign buffer bytes to the inflater.
    val head = source.buffer.head!!
    bufferBytesHeldByInflater = head.limit - head.pos
    inflater.setInput(head.data, head.pos, bufferBytesHeldByInflater)
    return false
  }

  /** When the inflater has processed compressed data, remove it from the buffer.  */
  private fun releaseInflatedBytes() {
    if (bufferBytesHeldByInflater == 0) return
    val toRelease = bufferBytesHeldByInflater - inflater.remaining
    bufferBytesHeldByInflater -= toRelease
    source.skip(toRelease.toLong())
  }

  override fun timeout(): Timeout = source.timeout()

  @Throws(IOException::class)
  override fun close() {
    if (closed) return
    inflater.end()
    closed = true
    source.close()
  }
}

/**
 * Returns an [InflaterSource] that DEFLATE-decompresses this [Source] while reading.
 *
 * @see InflaterSource
 */
inline fun Source.inflate(inflater: Inflater = Inflater()): InflaterSource =
    InflaterSource(this, inflater)
