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

package okio

/**
 * A [Source] which peeks into an upstream [BufferedSource] and allows reading and expanding of the
 * buffered data without consuming it. Does this by requesting additional data from the upstream
 * source if needed and copying out of the internal buffer of the upstream source if possible.
 *
 * This source also maintains a snapshot of the starting location of the upstream buffer which it
 * validates against on every read. If the upstream buffer is read from, this source will become
 * invalid and throw [IllegalStateException] on any future reads.
 */
internal class PeekSource(
  private val upstream: BufferedSource
) : Source {
  private val buffer = upstream.buffer
  private var expectedSegment = buffer.head
  private var expectedPos = buffer.head?.pos ?: -1

  private var closed = false
  private var pos = 0L

  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    // Source becomes invalid if there is an expected Segment and it and the expected position
    // do not match the current head and head position of the upstream buffer
    check(expectedSegment == null ||
      expectedSegment === buffer.head && expectedPos == buffer.head!!.pos) {
      "Peek source is invalid because upstream source was used"
    }
    if (byteCount == 0L) return 0L
    if (!upstream.request(pos + 1)) return -1L

    if (expectedSegment == null && buffer.head != null) {
      // Only once the buffer actually holds data should an expected Segment and position be
      // recorded. This allows reads from the peek source to repeatedly return -1 and for data to be
      // added later. Unit tests depend on this behavior.
      expectedSegment = buffer.head
      expectedPos = buffer.head!!.pos
    }

    val toCopy = minOf(byteCount, buffer.size - pos)
    buffer.copyTo(sink, pos, toCopy)
    pos += toCopy
    return toCopy
  }

  override fun timeout(): Timeout {
    return upstream.timeout()
  }

  override fun close() {
    closed = true
  }
}
