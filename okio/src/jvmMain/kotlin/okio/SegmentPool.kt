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

import java.util.concurrent.atomic.AtomicReference
import okio.SegmentPool.LOCK
import okio.SegmentPool.recycle
import okio.SegmentPool.take

/**
 * This class pools segments in a lock-free singly-linked stack. Though this code is lock-free it
 * does use a sentinel [LOCK] value to defend against races. Conflicted operations are not retried,
 * so there is no chance of blocking despite the term "lock".
 *
 * On [take], a caller swaps the stack's next pointer with the [LOCK] sentinel. If the stack was
 * not already locked, the caller replaces the head node with its successor.
 *
 * On [recycle], a caller swaps the stack's next pointer with the [LOCK] sentinel. If the stack was
 * not already locked, the caller replaces the head node with a new node whose successor is the
 * replaced head.
 *
 * On conflict, operations succeed, but segments are not pushed into the stack. For example, a
 * [take] that loses a race allocates a new segment regardless of the pool size. A [recycle] call
 * that loses a race will not increase the size of the pool. Under significant contention, this pool
 * will have fewer hits and the VM will do more GC and zero filling of arrays.
 *
 * This tracks the number of bytes in each linked list in its [Segment.limit] property. Each element
 * has a limit that's one segment size greater than its successor element. The maximum size of the
 * pool is a product of [MAX_SIZE] and [HASH_BUCKET_COUNT].
 */
internal actual object SegmentPool {
  /** The maximum number of bytes to pool per hash bucket. */
  // TODO: Is 64 KiB a good maximum size? Do we ever have that many idle segments?
  actual val MAX_SIZE = 64 * 1024 // 64 KiB.

  /** A sentinel segment to indicate that the linked list is currently being modified. */
  private val LOCK = Segment(ByteArray(0), pos = 0, limit = 0, shared = false, owner = false)

  /**
   * The number of hash buckets. This number needs to balance keeping the pool small and contention
   * low. We use the number of processors rounded up to the nearest power of two. For example a
   * machine with 6 cores will have 8 hash buckets.
   */
  private val HASH_BUCKET_COUNT =
    Integer.highestOneBit(Runtime.getRuntime().availableProcessors() * 2 - 1)

  /**
   * Hash buckets each contain a singly-linked list of segments. The index/key is a hash function of
   * thread ID because it may reduce contention or increase locality.
   *
   * We don't use [ThreadLocal] because we don't know how many threads the host process has and we
   * don't want to leak memory for the duration of a thread's life.
   */
  private val hashBuckets: Array<AtomicReference<Segment?>> = Array(HASH_BUCKET_COUNT) {
    AtomicReference<Segment?>() // null value implies an empty bucket
  }

  actual val byteCount: Int
    get() {
      val first = firstRef().get() ?: return 0
      return first.limit
    }

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
        first.limit = 0
        return first
      }
    }
  }

  @JvmStatic
  actual fun recycle(segment: Segment) {
    require(segment.next == null && segment.prev == null)
    if (segment.shared) return // This segment cannot be recycled.

    val firstRef = firstRef()

    val first = firstRef.getAndSet(LOCK)
    if (first === LOCK) return // A take() or recycle() is currently in progress.
    val firstLimit = first?.limit ?: 0
    if (firstLimit >= MAX_SIZE) {
      firstRef.set(first) // Pool is full.
      return
    }

    segment.next = first
    segment.pos = 0
    segment.limit = firstLimit + Segment.SIZE

    firstRef.set(segment)
  }

  private fun firstRef(): AtomicReference<Segment?> {
    // Get a value in [0..HASH_BUCKET_COUNT) based on the current thread.
    val hashBucket = (Thread.currentThread().id and (HASH_BUCKET_COUNT - 1L)).toInt()
    return hashBuckets[hashBucket]
  }
}
