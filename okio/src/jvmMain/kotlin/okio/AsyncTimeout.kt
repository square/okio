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

import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import okio.AsyncTimeout.Companion.IDLE_TIMEOUT_NANOS
import okio.AsyncTimeout.Companion.condition
import okio.AsyncTimeout.Companion.idleSentinel
import okio.AsyncTimeout.Companion.queue

/**
 * This timeout uses a background thread to take action exactly when the timeout occurs. Use this to
 * implement timeouts where they aren't supported natively, such as to sockets that are blocked on
 * writing.
 *
 * Subclasses should override [timedOut] to take action when a timeout occurs. This method will be
 * invoked by the shared watchdog thread so it should not do any long-running operations. Otherwise,
 * we risk starving other timeouts from being triggered.
 *
 * Use [sink] and [source] to apply this timeout to a stream. The returned value will apply the
 * timeout to each operation on the wrapped stream.
 *
 * Callers should call [enter] before doing work that is subject to timeouts, and [exit] afterward.
 * The return value of [exit] indicates whether a timeout was triggered. Note that the call to
 * [timedOut] is asynchronous, and may be called after [exit].
 */
open class AsyncTimeout : Timeout() {
  private var state = STATE_IDLE

  /** Index in [queue], or -1 if this isn't currently in the heap. */
  @JvmField internal var index: Int = -1

  /** If scheduled, this is the time that the watchdog should time this out.  */
  private var timeoutAt: Long = 0L

  fun enter() {
    val timeoutNanos = timeoutNanos()
    val hasDeadline = hasDeadline()
    if (timeoutNanos == 0L && !hasDeadline) {
      return // No timeout and no deadline? Don't bother with the queue.
    }

    lock.withLock {
      check(state == STATE_IDLE) { "Unbalanced enter/exit" }
      state = STATE_IN_QUEUE
      insertIntoQueue(this)
    }
  }

  /** Returns true if the timeout occurred.  */
  fun exit(): Boolean {
    lock.withLock {
      val oldState = this.state
      state = STATE_IDLE

      if (oldState == STATE_IN_QUEUE) {
        queue.remove(this)
        return false
      } else {
        return oldState == STATE_TIMED_OUT
      }
    }
  }

  override fun cancel() {
    super.cancel()

    lock.withLock {
      if (state == STATE_IN_QUEUE) {
        queue.remove(this)
        state = STATE_CANCELED
      }
    }
  }

  /**
   * Returns the amount of time left until the time out. This will be negative if the timeout has
   * elapsed and the timeout should occur immediately.
   */
  internal fun remainingNanos(now: Long) = timeoutAt - now

  /** If scheduled, this is the expiration time of this timeout  */
  internal fun timeoutAt(): Long = timeoutAt

  /**
   * Sets the timeoutAt value as a sum of [now] and the time to wait for this timeout.
   */
  internal fun setTimeoutAt(now: Long = System.nanoTime()) {
    val timeoutNanos = timeoutNanos()
    val hasDeadline = hasDeadline()
    if (timeoutNanos() != 0L && hasDeadline()) {
      // Compute the earliest event; either timeout or deadline. Because nanoTime can wrap
      // around, minOf() is undefined for absolute values, but meaningful for relative ones.
      timeoutAt = now + minOf(timeoutNanos, deadlineNanoTime() - now)
    } else if (timeoutNanos != 0L) {
      timeoutAt = now + timeoutNanos
    } else if (hasDeadline) {
      timeoutAt = deadlineNanoTime()
    } else {
      throw AssertionError()
    }
  }

  /**
   * Invoked by the watchdog thread when the time between calls to [enter] and [exit] has exceeded
   * the timeout.
   */
  protected open fun timedOut() {}

  /**
   * Returns a new sink that delegates to [sink], using this to implement timeouts. This works
   * best if [timedOut] is overridden to interrupt [sink]'s current operation.
   */
  fun sink(sink: Sink): Sink {
    return object : Sink {
      override fun write(source: Buffer, byteCount: Long) {
        checkOffsetAndCount(source.size, 0, byteCount)

        var remaining = byteCount
        while (remaining > 0L) {
          // Count how many bytes to write. This loop guarantees we split on a segment boundary.
          var toWrite = 0L
          var s = source.head!!
          while (toWrite < TIMEOUT_WRITE_SIZE) {
            val segmentSize = s.limit - s.pos
            toWrite += segmentSize.toLong()
            if (toWrite >= remaining) {
              toWrite = remaining
              break
            }
            s = s.next!!
          }

          // Emit one write. Only this section is subject to the timeout.
          withTimeout { sink.write(source, toWrite) }
          remaining -= toWrite
        }
      }

      override fun flush() {
        withTimeout { sink.flush() }
      }

      override fun close() {
        withTimeout { sink.close() }
      }

      override fun timeout() = this@AsyncTimeout

      override fun toString() = "AsyncTimeout.sink($sink)"
    }
  }

  /**
   * Returns a new source that delegates to [source], using this to implement timeouts. This works
   * best if [timedOut] is overridden to interrupt [source]'s current operation.
   */
  fun source(source: Source): Source {
    return object : Source {
      override fun read(sink: Buffer, byteCount: Long): Long {
        return withTimeout { source.read(sink, byteCount) }
      }

      override fun close() {
        withTimeout { source.close() }
      }

      override fun timeout() = this@AsyncTimeout

      override fun toString() = "AsyncTimeout.source($source)"
    }
  }

  /**
   * Surrounds [block] with calls to [enter] and [exit], throwing an exception from
   * [newTimeoutException] if a timeout occurred.
   */
  inline fun <T> withTimeout(block: () -> T): T {
    var throwOnTimeout = false
    enter()
    try {
      val result = block()
      throwOnTimeout = true
      return result
    } catch (e: IOException) {
      throw if (!exit()) e else `access$newTimeoutException`(e)
    } finally {
      val timedOut = exit()
      if (timedOut && throwOnTimeout) throw `access$newTimeoutException`(null)
    }
  }

  @PublishedApi // Binary compatible trampoline function
  internal fun `access$newTimeoutException`(cause: IOException?) = newTimeoutException(cause)

  /**
   * Returns an [IOException] to represent a timeout. By default this method returns
   * [InterruptedIOException]. If [cause] is non-null it is set as the cause of the
   * returned exception.
   */
  protected open fun newTimeoutException(cause: IOException?): IOException {
    val e = InterruptedIOException("timeout")
    if (cause != null) {
      e.initCause(cause)
    }
    return e
  }

  private class Watchdog : Thread("Okio Watchdog") {
    init {
      isDaemon = true
    }

    override fun run() {
      while (true) {
        try {
          var timedOut: AsyncTimeout?
          lock.withLock {
            timedOut = awaitTimeout()

            // The queue is completely empty. Let this thread exit and let another watchdog thread
            // get created on the next call to scheduleTimeout().
            if (timedOut === idleSentinel) {
              idleSentinel = null
              return
            }
          }

          // Close the timed out node, if one was found.
          timedOut?.timedOut()
        } catch (_: InterruptedException) {
        }
      }
    }
  }

  private companion object {
    /**
     * The watchdog thread processes this queue containing pending timeouts. It synchronizes on
     * [condition] to guard accesses to the queue.
     *
     * The queue's first element is the next node to time out, which is null if the queue is empty.
     *
     * The [idleSentinel] is null until the watchdog thread is started and also after being
     * idle for [AsyncTimeout.IDLE_TIMEOUT_MILLIS].
     */
    val queue = PriorityQueue()
    var idleSentinel: AsyncTimeout? = null
    val lock: ReentrantLock = ReentrantLock()
    val condition: Condition = lock.newCondition()

    /**
     * Don't write more than 64 KiB of data at a time, give or take a segment. Otherwise, slow
     * connections may suffer timeouts even when they're making (slow) progress. Without this,
     * writing a single 1 MiB buffer may never succeed on a sufficiently slow connection.
     */
    private const val TIMEOUT_WRITE_SIZE = 64 * 1024

    /** Duration for the watchdog thread to be idle before it shuts itself down.  */
    private val IDLE_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60)
    private val IDLE_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(IDLE_TIMEOUT_MILLIS)

    /*
     *                                       .-------------.
     *                                       |             |
     *            .------------ exit() ------|  CANCELED   |
     *            |                          |             |
     *            |                          '-------------'
     *            |                                 ^
     *            |                                 |  cancel()
     *            v                                 |
     *     .-------------.                   .-------------.
     *     |             |---- enter() ----->|             |
     *     |    IDLE     |                   |  IN QUEUE   |
     *     |             |<---- exit() ------|             |
     *     '-------------'                   '-------------'
     *            ^                                 |
     *            |                                 |  time out
     *            |                                 v
     *            |                          .-------------.
     *            |                          |             |
     *            '------------ exit() ------|  TIMED OUT  |
     *                                       |             |
     *                                       '-------------'
     *
     * Notes:
     *  * enter() crashes if called from a state other than IDLE.
     *  * If there's no timeout (ie. wait forever), then enter() is a no-op. There's no state to
     *    track entered but not in the queue.
     *  * exit() is a no-op from IDLE. This is probably too lenient, but it made it simpler for
     *    early implementations to support cases where enter() as a no-op.
     *  * cancel() is a no-op from every state but IN QUEUE.
     */

    private const val STATE_IDLE = 0
    private const val STATE_IN_QUEUE = 1
    private const val STATE_TIMED_OUT = 2
    private const val STATE_CANCELED = 3

    private fun insertIntoQueue(node: AsyncTimeout) {
      // Start the watchdog thread and create the sentinel node when the first timeout is scheduled.
      if (idleSentinel == null) {
        idleSentinel = AsyncTimeout()
        Watchdog().start()
      }
      node.setTimeoutAt()
      // Insert the node into the queue.
      queue.add(node)
      if (node.index == 1) {
        // Wake up the watchdog when inserting at the front.
        condition.signal()
      }
    }

    /**
     * Removes and returns the next node to timeout, waiting for it to time out if necessary.
     *
     * This returns [idleSentinel] if the queue was empty when starting, and it continues to be
     * empty after waiting [IDLE_TIMEOUT_NANOS].
     *
     * This returns null if a new node was inserted while waiting.
     */
    @Throws(InterruptedException::class)
    fun awaitTimeout(): AsyncTimeout? {
      // Get the next eligible node.
      val node = queue.first()

      // The queue is empty. Wait until either something is enqueued or the idle timeout elapses.
      if (node == null) {
        val startNanos = System.nanoTime()
        condition.await(IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        return if (queue.first() == null && System.nanoTime() - startNanos >= IDLE_TIMEOUT_NANOS) {
          idleSentinel // The idle timeout elapsed.
        } else {
          null // The situation has changed.
        }
      }

      val waitNanos = node.remainingNanos(System.nanoTime())

      // The first node in the queue hasn't timed out yet. Await that.
      if (waitNanos > 0) {
        condition.await(waitNanos, TimeUnit.NANOSECONDS)
        return null
      }

      // The first node in the queue has timed out. Remove it.
      queue.remove(node)
      node.state = STATE_TIMED_OUT
      return node
    }
  }
}

/**
 * A min-heap binary heap, stored in an array.
 *
 * Nodes are [AsyncTimeout] instances directly. To support fast random removals, each [AsyncTimeout]
 * knows its index in the heap.
 *
 * The first node is at array index 1.
 *
 * https://en.wikipedia.org/wiki/Binary_heap
 */
internal class PriorityQueue {
  @JvmField
  internal var size = 0

  @JvmField
  internal var array = arrayOfNulls<AsyncTimeout?>(8)

  fun first(): AsyncTimeout? = array[1]

  fun add(node: AsyncTimeout) {
    val newSize = size + 1
    size = newSize
    if (newSize == array.size) {
      val doubledArray = arrayOfNulls<AsyncTimeout?>(newSize * 2)
      array.copyInto(doubledArray)
      array = doubledArray
    }

    heapifyUp(newSize, node)
  }

  fun remove(node: AsyncTimeout) {
    require(node.index != -1)
    val oldSize = size

    // Take the heap's last node to fill this node's position.
    val removedIndex = node.index
    val last = array[oldSize]!!
    node.index = -1
    array[oldSize] = null
    size = oldSize - 1

    if (node === last) return // The last node is the removed node.

    val nodeCompareToLast = node.compareTo(last)
    when {
      // The last node fits in the vacated spot.
      nodeCompareToLast == 0 -> {
        array[removedIndex] = last
        last.index = removedIndex
      }

      // The last node might be too large for the vacated spot.
      nodeCompareToLast < 0 -> heapifyDown(removedIndex, last)

      // The last node might be too small for the vacated spot.
      else -> heapifyUp(removedIndex, last)
    }
  }

  /**
   * Put [node] in the right position in the heap by moving it up the heap.
   *
   * When this is done it'll put something in [vacantIndex], and [node] somewhere in the heap.
   *
   * @param vacantIndex an index in [array] that is vacant.
   */
  private fun heapifyUp(
    vacantIndex: Int,
    node: AsyncTimeout,
  ) {
    var vacantIndex = vacantIndex
    while (true) {
      val parentIndex = vacantIndex shr 1
      if (parentIndex == 0) break // No parent.

      val parentNode = array[parentIndex]!!
      if (parentNode <= node) break // No need to swap with the parent.

      // Put our parent in the vacant index, and its index is the new vacant index.
      parentNode.index = vacantIndex
      array[vacantIndex] = parentNode
      vacantIndex = parentIndex
    }

    array[vacantIndex] = node
    node.index = vacantIndex
  }

  /**
   * Put [node] in the right position in the heap by moving it down the heap.
   *
   * When this is done it'll put something in [vacantIndex], and [node] somewhere in the heap.
   *
   * @param vacantIndex an index in [array] that is vacant.
   */
  private fun heapifyDown(
    vacantIndex: Int,
    node: AsyncTimeout,
  ) {
    var vacantIndex = vacantIndex
    while (true) {
      val leftIndex = vacantIndex shl 1
      val rightIndex = leftIndex + 1

      val smallestChild = when {
        rightIndex <= size -> {
          val leftNode = array[leftIndex]!!
          val rightNode = array[rightIndex]!!
          when {
            leftNode < rightNode -> leftNode
            else -> rightNode
          }
        }
        leftIndex <= size -> {
          array[leftIndex]!! // Left node.
        }
        else -> break // No children.
      }

      if (node <= smallestChild) break // No need to swap with the children.

      // Put our smallest child in the vacant index, and its index is the new vacant index.
      val newVacantIndex = smallestChild.index
      smallestChild.index = vacantIndex
      array[vacantIndex] = smallestChild
      vacantIndex = newVacantIndex
    }

    array[vacantIndex] = node
    node.index = vacantIndex
  }

  /**
   * Compares timeouts by their [AsyncTimeout.timeoutAt] values, in ascending order.
   */
  @Suppress("NOTHING_TO_INLINE")
  private inline operator fun AsyncTimeout.compareTo(other: AsyncTimeout): Int {
    val a = timeoutAt()
    val b = other.timeoutAt()
    return 0L.compareTo(b - a)
  }
}
