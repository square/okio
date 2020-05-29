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
  private val maxSegmentCount = MAX_SIZE / Segment.SIZE


  /** A sentinel segment to indicate that the linked list is currently being modified. */
  private val LOCK = Segment(ByteArray(0), pos = 0, limit = 0, shared = false, owner = false)

  /**
   * 16 hash buckets, each containing a singly-linked list of segments. We use multiple hash buckets
   * so different threads don't race each other. We use thread IDs as hash keys because they're
   * handy, and because it may increase locality.
   *
   * We don't use [ThreadLocal] because we don't know how many threads the host process has and we
   * don't want to leak memory for the duration of a thread's life.
   */
  private val hashBucketCount = Runtime.getRuntime().availableProcessors()
  private val hashBuckets: Array<AtomicReference<Segment?>> = Array(hashBucketCount) {
    AtomicReference<Segment?>()
  }

  // Not optimized, use for stats or so.
  actual val byteCount: Long
    get() = hashBuckets.map{ first -> getSegmentCount(first.get()) }.sum() * Segment.SIZE

  @JvmStatic
  actual fun take(): Segment {
    val firstRef = firstRef()

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
        return first
      }
    }
  }

  private fun getSegmentCount(first: Segment?): Long =
    first?.freeSegmentCount ?: 0L

  @JvmStatic
  actual fun recycle(segment: Segment) {
    require(segment.next == null && segment.prev == null)
    if (segment.shared) return // This segment cannot be recycled.

    val firstRef = firstRef()

    val first = firstRef.get()
    if (first === LOCK) return // A take() is currently in progress.
    val wasSegmentCount = getSegmentCount(first)
    if (wasSegmentCount >= maxSegmentCount) return // Pool is full.

    segment.next = first
    segment.limit = 0
    segment.pos = 0
    segment.freeSegmentCount = wasSegmentCount + 1

    if (!firstRef.compareAndSet(first, segment)) segment.next = null
    // If we raced another operation: Don't recycle this segment.
  }

  private fun firstRef(): AtomicReference<Segment?> {
    val hashBucket = (Thread.currentThread().id % hashBucketCount).toInt() // Get a value in [0..hashBucketCount).
    return hashBuckets[hashBucket]
  }
}
