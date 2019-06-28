/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package okio

class DeflaterSink internal constructor(
  private val sink: Sink,
  private val deflater: Deflater = Deflater()
) : Sink {
  private val buffer = Buffer()
  private var closed = false

  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)

    var remaining = byteCount
    while (remaining > 0) {
      // Share bytes from the head segment of 'source' with the deflater.
      val head = source.head!!
      val toDeflate = minOf(remaining, head.limit - head.pos).toInt()
      deflater.setInput(head.data, head.pos, toDeflate)

      // Deflate those bytes into sink.
      deflate(false)

      // Mark those bytes as read.
      source.size -= toDeflate
      head.pos += toDeflate
      if (head.pos == head.limit) {
        source.head = head.pop()
        SegmentPool.recycle(head)
      }

      remaining -= toDeflate
    }
  }

  private fun deflate(syncFlush: Boolean) {
    while (true) {
      val s = buffer.writableSegment(1)
      val deflated = deflater.deflate(s.data, s.limit, Segment.SIZE - s.limit, flush = syncFlush)

      if (deflated > 0) {
        s.limit += deflated
        buffer.size += deflated

        sink.write(buffer, buffer.size) // TODO emitCompleteSegments
      } else if (deflater.needsInput()) {
        if (s.pos == s.limit) {
          // We allocated a tail segment, but didn't end up needing it. Recycle!
          buffer.head = s.pop()
          SegmentPool.recycle(s)
        }
        return
      }
    }
  }

  override fun flush() {
    deflate(true)
    sink.write(buffer, buffer.size) // TODO emitCompleteSegments
    sink.flush()
  }

  internal fun finishDeflate() {
    deflater.finish()
    deflate(false)
  }

  override fun timeout(): Timeout = sink.timeout()

  override fun close() {
    if (closed) return

    // Emit deflated data to the underlying sink. If this fails, we still need
    // to close the deflater and the sink; otherwise we risk leaking resources.
    var thrown: Throwable? = null
    try {
      finishDeflate()
    } catch (e: Throwable) {
      thrown = e
    }

    try {
      deflater.end()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    try {
      sink.write(buffer, buffer.size) // TODO emitCompleteSegments
      sink.close()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    closed = true

    if (thrown != null) throw thrown
  }
}
