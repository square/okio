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

@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package okio

import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater

actual class InflaterSource internal actual constructor(
  private val source: BufferedSource,
  private val inflater: Inflater,
) : Source {

  /**
   * When we call Inflater.setInput(), the inflater keeps our byte array until it needs input again.
   * This tracks how many bytes the inflater is currently holding on to.
   */
  private var bufferBytesHeldByInflater = 0
  private var closed = false

  actual constructor(source: Source, inflater: Inflater) : this(source.buffer(), inflater)

  @Throws(IOException::class)
  actual override fun read(sink: Buffer, byteCount: Long): Long {
    while (true) {
      val bytesInflated = readOrInflate(sink, byteCount)
      if (bytesInflated > 0) return bytesInflated
      if (inflater.finished() || inflater.needsDictionary()) return -1L
      if (source.exhausted()) throw EOFException("source exhausted prematurely")
    }
  }

  /**
   * Consume deflated bytes from the underlying source, and write any inflated bytes to [sink].
   * Returns the number of inflated bytes written to [sink]. This may return 0L, though it will
   * always consume 1 or more bytes from the underlying source if it is not exhausted.
   *
   * Use this instead of [read] when it is useful to consume the deflated stream even when doing so
   * doesn't yield inflated bytes.
   */
  @Throws(IOException::class)
  fun readOrInflate(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    if (byteCount == 0L) return 0L

    try {
      // Prepare the destination that we'll write into.
      val tail = sink.writableSegment(1)
      val toRead = minOf(byteCount, Segment.SIZE - tail.limit).toInt()

      // Prepare the source that we'll read from.
      refill()

      // Decompress the inflater's compressed data into the sink.
      val bytesInflated = inflater.inflate(tail.data, tail.limit, toRead)

      // Release consumed bytes from the source.
      releaseBytesAfterInflate()

      // Track produced bytes in the destination.
      if (bytesInflated > 0) {
        tail.limit += bytesInflated
        sink.size += bytesInflated
        return bytesInflated.toLong()
      }

      // We allocated a tail segment, but didn't end up needing it. Recycle!
      if (tail.pos == tail.limit) {
        sink.head = tail.pop()
        SegmentPool.recycle(tail)
      }

      return 0L
    } catch (e: DataFormatException) {
      throw IOException(e)
    }
  }

  /**
   * Refills the inflater with compressed data if it needs input. (And only if it needs input).
   * Returns true if the inflater required input but the source was exhausted.
   */
  @Throws(IOException::class)
  fun refill(): Boolean {
    if (!inflater.needsInput()) return false

    // If there are no further bytes in the source, we cannot refill.
    if (source.exhausted()) return true

    // Assign buffer bytes to the inflater.
    val head = source.buffer.head!!
    bufferBytesHeldByInflater = head.limit - head.pos
    inflater.setInput(head.data, head.pos, bufferBytesHeldByInflater)
    return false
  }

  /** When the inflater has processed compressed data, remove it from the buffer.  */
  private fun releaseBytesAfterInflate() {
    if (bufferBytesHeldByInflater == 0) return
    val toRelease = bufferBytesHeldByInflater - inflater.remaining
    bufferBytesHeldByInflater -= toRelease
    source.skip(toRelease.toLong())
  }

  actual override fun timeout(): Timeout = source.timeout()

  @Throws(IOException::class)
  actual override fun close() {
    if (closed) return
    inflater.end()
    closed = true
    source.close()
  }
}
