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

import okio.SegmentPool.LOCK
import okio.SegmentPool.MAX_SIZE
import okio.SegmentPool.recycle
import okio.SegmentPool.take
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * This class pools segments in a lock-free singly-linked stack. Though this code is lock-free it
 * does use a sentinel [LOCK] value to defend against races.
 *
 * When popping, a caller swaps the stack's next pointer with the [LOCK] sentinel. If the stack was
 * not already locked, the caller replaces the head node with its successor.
 *
 * When pushing, a caller swaps the head with a new node whose successor is the replaced head.
 *
 * If operations conflict, segments are not pushed into the stack. A [recycle] call that loses a
 * race will not add to the pool, and a [take] call that loses a race will not take from the pool.
 * Under significant contention this pool will have fewer hits and the VM will do more GC and zero
 * filling of arrays.
 *
 * Note that the [MAX_SIZE] may be exceeded if multiple calls to [recycle] race. Exceeding the
 * target pool size by a few segments doesn't harm performance, and imperfect enforcement is less
 * code.
 */
internal actual object SegmentPool {
  /** The maximum number of bytes to pool.  */
  // TODO: Is 64 KiB a good maximum size? Do we ever have that many idle segments?
  actual val MAX_SIZE = 64 * 1024L // 64 KiB.

  /** A sentinel segment to indicate that the linked list is currently being modified. */
  private val LOCK = Segment(ByteArray(0), pos = 0, limit = 0, shared = false, owner = false)

  /** Singly-linked list of segments. */
  private var firstRef = AtomicReference<Segment?>()

  /** Total bytes in this pool. */
  private var atomicByteCount = AtomicLong()

  actual val byteCount: Long
    get() = atomicByteCount.get()

  @JvmStatic
  actual fun take(): Segment {
    val first = firstRef.getAndSet(LOCK)
    when {
      first === LOCK -> {
        // We didn't acquire the lock. Don't take a pooled segment.
        return Segment()
      }
      first == null -> {
        // We acquired the lock but the pool was empty. Unlock and return a new segment.
        firstRef.set(null)
        return Segment()
      }
      else -> {
        // We acquired the lock and the pool was not empty. Pop the first element and return it.
        firstRef.set(first.next)
        first.next = null
        atomicByteCount.addAndGet(-Segment.SIZE.toLong())
        return first
      }
    }
  }

  @JvmStatic
  actual fun recycle(segment: Segment) {
    require(segment.next == null && segment.prev == null)
    if (segment.shared) return // This segment cannot be recycled.
    if (atomicByteCount.get() >= MAX_SIZE) return // Pool is full.

    val first = firstRef.get()
    if (first === LOCK) return // A take() is currently in progress.

    segment.next = first
    segment.limit = 0
    segment.pos = 0

    if (firstRef.compareAndSet(first, segment)) {
      // We successfully recycled this segment. Adjust the pool size.
      atomicByteCount.addAndGet(Segment.SIZE.toLong())
    } else {
      // We raced another operation. Don't recycle this segment.
      segment.next = null
    }
  }
}
