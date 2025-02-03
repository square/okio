/*
 * Copyright (C) 2020 Square, Inc.
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
package okio

import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSOutputStream
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusNotOpen
import platform.posix.uint8_tVar

/** Returns a sink that writes to `out`. */
fun NSOutputStream.sink(): Sink = OutputStreamSink(this)

@OptIn(UnsafeNumber::class)
private open class OutputStreamSink(
  private val out: NSOutputStream,
) : Sink {

  init {
    if (out.streamStatus == NSStreamStatusNotOpen) out.open()
  }

  override fun write(source: Buffer, byteCount: Long) {
    if (out.streamStatus == NSStreamStatusClosed) throw IOException("Stream Closed")

    checkOffsetAndCount(source.size, 0, byteCount)
    var remaining = byteCount
    while (remaining > 0) {
      val head = source.head!!
      val toCopy = minOf(remaining, head.limit - head.pos).toInt()
      val bytesWritten = head.data.usePinned {
        val bytes = it.addressOf(head.pos).reinterpret<uint8_tVar>()
        out.write(bytes, toCopy.convert()).toLong()
      }

      if (bytesWritten < 0L) throw IOException(out.streamError?.localizedDescription ?: "Unknown error")
      if (bytesWritten == 0L) throw IOException("NSOutputStream reached capacity")

      head.pos += bytesWritten.toInt()
      remaining -= bytesWritten
      source.size -= bytesWritten

      if (head.pos == head.limit) {
        source.head = head.pop()
        SegmentPool.recycle(head)
      }
    }
  }

  override fun flush() {
    // no-op
  }

  override fun close() = out.close()

  override fun timeout(): Timeout = Timeout.NONE

  override fun toString() = "RawSink($out)"
}
