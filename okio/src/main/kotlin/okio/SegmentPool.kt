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
package okio

/**
 * A collection of unused segments, necessary to avoid GC churn and zero-fill.
 * This pool is a thread-safe static singleton.
 */
internal object SegmentPool {
  /** The maximum number of bytes to pool.  */
  // TODO: Is 64 KiB a good maximum size? Do we ever have that many idle segments?
  const val MAX_SIZE = 64 * 1024L // 64 KiB.

  /** Singly-linked list of segments.  */
  @JvmField
  var next: Segment? = null

  /** Total bytes in this pool.  */
  @JvmField
  var byteCount = 0L

  @JvmStatic
  fun take(): Segment {
    synchronized(this) {
      next?.let { result ->
        next = result.next
        result.next = null
        byteCount -= Segment.SIZE
        return result
      }
    }
    return Segment() // Pool is empty. Don't zero-fill while holding a lock.
  }

  @JvmStatic
  fun recycle(segment: Segment) {
    require(segment.next == null && segment.prev == null)
    if (segment.shared) return // This segment cannot be recycled.

    synchronized(this) {
      if (byteCount + Segment.SIZE > MAX_SIZE) return // Pool is full.
      byteCount += Segment.SIZE
      segment.next = next
      segment.limit = 0
      segment.pos = segment.limit
      next = segment
    }
  }
}
