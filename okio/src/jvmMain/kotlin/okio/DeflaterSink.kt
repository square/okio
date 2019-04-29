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

@file:JvmName("-DeflaterSinkExtensions")
@file:Suppress("NOTHING_TO_INLINE") // Aliases to public API.

package okio

import java.io.IOException
import java.util.zip.Deflater
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

/**
 * A sink that uses [DEFLATE](http://tools.ietf.org/html/rfc1951) to
 * compress data written to another source.
 *
 * ### Sync flush
 *
 * Aggressive flushing of this stream may result in reduced compression. Each
 * call to [flush] immediately compresses all currently-buffered data;
 * this early compression may be less effective than compression performed
 * without flushing.
 *
 * This is equivalent to using [Deflater] with the sync flush option.
 * This class does not offer any partial flush mechanism. For best performance,
 * only call [flush] when application behavior requires it.
 */
class DeflaterSink
/**
 * This internal constructor shares a buffer with its trusted caller.
 * In general we can't share a BufferedSource because the deflater holds input
 * bytes until they are inflated.
 */
internal constructor(private val sink: BufferedSink, private val deflater: Deflater) : Sink {
  constructor(sink: Sink, deflater: Deflater) : this(sink.buffer(), deflater)

  private var closed = false

  @Throws(IOException::class)
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

  @IgnoreJRERequirement
  private fun deflate(syncFlush: Boolean) {
    val buffer = sink.buffer
    while (true) {
      val s = buffer.writableSegment(1)

      // The 4-parameter overload of deflate() doesn't exist in the RI until
      // Java 1.7, and is public (although with @hide) on Android since 2.3.
      // The @hide tag means that this code won't compile against the Android
      // 2.3 SDK, but it will run fine there.
      val deflated = if (syncFlush) {
        deflater.deflate(s.data, s.limit, Segment.SIZE - s.limit, Deflater.SYNC_FLUSH)
      } else {
        deflater.deflate(s.data, s.limit, Segment.SIZE - s.limit)
      }

      if (deflated > 0) {
        s.limit += deflated
        buffer.size += deflated
        sink.emitCompleteSegments()
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

  @Throws(IOException::class)
  override fun flush() {
    deflate(true)
    sink.flush()
  }

  internal fun finishDeflate() {
    deflater.finish()
    deflate(false)
  }

  @Throws(IOException::class)
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
      sink.close()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    closed = true

    if (thrown != null) throw thrown
  }

  override fun timeout(): Timeout = sink.timeout()

  override fun toString() = "DeflaterSink($sink)"
}

/**
 * Returns an [DeflaterSink] that DEFLATE-compresses data to this [Sink] while writing.
 *
 * @see DeflaterSink
 */
inline fun Sink.deflate(deflater: Deflater = Deflater()): DeflaterSink =
    DeflaterSink(this, deflater)
