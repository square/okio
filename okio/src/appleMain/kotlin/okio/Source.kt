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

import kotlinx.cinterop.*
import platform.Foundation.NSInputStream
import platform.darwin.UInt8Var

fun NSInputStream.source(): Source = NSInputStreamSource(this)

private open class NSInputStreamSource(
  val input: NSInputStream
) : Source {

  init {
    input.open()
  }

  @OptIn(UnsafeNumber::class)
  override fun read(sink: Buffer, byteCount: Long): Long {
    if (byteCount == 0L) return 0L
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    val tail = sink.writableSegment(1)
    val maxToCopy = minOf(byteCount, Segment.SIZE - tail.limit)
    val bytesRead = tail.data.usePinned {
      val bytes = it.addressOf(tail.limit).reinterpret<UInt8Var>()
      input.read(bytes, maxToCopy.toULong())
    }
    if (bytesRead < 0) throw IOException(input.streamError?.localizedDescription)
    if (bytesRead == 0L) {
      if (tail.pos == tail.limit) {
        // We allocated a tail segment, but didn't end up needing it. Recycle!
        sink.head = tail.pop()
        SegmentPool.recycle(tail)
      }
      return -1
    }
    tail.limit += bytesRead.toInt()
    sink.size += bytesRead
    return bytesRead
  }

  override fun close() = input.close()

  override fun timeout() = Timeout.NONE

  override fun toString() = "source($input)"
}
