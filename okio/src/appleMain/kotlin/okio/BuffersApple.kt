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
@file:OptIn(UnsafeNumber::class)

package okio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Foundation.data
import platform.darwin.NSUIntegerMax
import platform.posix.errno
import platform.posix.malloc
import platform.posix.memcpy
import platform.posix.strerror
import platform.posix.uint8_tVar

@OptIn(ExperimentalForeignApi::class)
internal fun Buffer.write(source: CPointer<uint8_tVar>, maxLength: Int) {
  require(maxLength >= 0) { "maxLength ($maxLength) must not be negative" }

  var currentOffset = 0
  while (currentOffset < maxLength) {
    val tail = writableSegment(1)

    val toCopy = minOf(maxLength - currentOffset, Segment.SIZE - tail.limit)
    tail.data.usePinned {
      memcpy(it.addressOf(tail.pos), source + currentOffset, toCopy.convert())
    }

    currentOffset += toCopy
    tail.limit += toCopy
  }
  size += maxLength
}

internal fun Buffer.read(sink: CPointer<uint8_tVar>, maxLength: Int): Int {
  require(maxLength >= 0) { "maxLength ($maxLength) must not be negative" }

  val s = head ?: return 0
  val toCopy = minOf(maxLength, s.limit - s.pos)
  s.data.usePinned {
    memcpy(sink, it.addressOf(s.pos), toCopy.convert())
  }

  s.pos += toCopy
  size -= toCopy.toLong()

  if (s.pos == s.limit) {
    head = s.pop()
    SegmentPool.recycle(s)
  }

  return toCopy
}

@OptIn(BetaInteropApi::class)
internal fun Buffer.snapshotAsNSData(): NSData {
  if (size == 0L) return NSData.data()

  check(size.toULong() <= NSUIntegerMax) { "Buffer is too long ($size) to be converted into NSData." }

  val bytes = malloc(size.convert())?.reinterpret<uint8_tVar>()
    ?: throw Error("malloc failed: ${strerror(errno)?.toKString()}")
  var curr = head
  var index = 0
  do {
    check(curr != null) { "Current segment is null" }
    val pos = curr.pos
    val length = curr.limit - pos
    curr.data.usePinned {
      memcpy(bytes + index, it.addressOf(pos), length.convert())
    }
    curr = curr.next
    index += length
  } while (curr !== head)
  return NSData.create(bytesNoCopy = bytes, length = size.convert())
}
