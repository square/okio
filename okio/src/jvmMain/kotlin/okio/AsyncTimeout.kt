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

  /** Index in the heap, or -1 if this isn't currently in the heap. */
  internal var index: Int = -1

  /** If scheduled, this is the time that the watchdog should time this out.  */
  private var timeoutAt = 0L

  fun enter() {
    val timeoutNanos = timeoutNanos()
    val hasDeadline = hasDeadline()
    if (timeoutNanos == 0L && !hasDeadline) {
      return // No timeout and no deadline? Don't bother with the queue.
    }

    lock.withLock {
      check(state == STATE_IDLE) { "Unbalanced enter/exit" }
      state = STATE_IN_QUEUE
      insertIntoQueue(this, timeoutNanos, hasDeadline)
    }
  }

  /** Returns true if the timeout occurred.  */
  fun exit(): Boolean {
    lock.withLock {
      val oldState = this.state
      state = STATE_IDLE

      if (oldState == STATE_IN_QUEUE) {
        dataStructure.removeFromQueue(this)
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
        dataStructure.removeFromQueue(this)
        state = STATE_CANCELED
      }
    }
  }

  /**
   * Returns the amount of time left until the time out. This will be negative if the timeout has
   * elapsed and the timeout should occur immediately.
   */
  internal fun remainingNanos(now: Long) = timeoutAt - now

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
            if (timedOut === dataStructure.head) {
              dataStructure.head = null
              return
            }
          }

          // Close the timed out node, if one was found.
          timedOut?.timedOut()
        } catch (ignored: InterruptedException) {
        }
      }
    }
  }

  private companion object {
    val lock: ReentrantLock = ReentrantLock()
    val condition: Condition = lock.newCondition()

    /**
     * Don't write more than 64 KiB of data at a time, give or take a segment. Otherwise slow
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

    /**
     * The watchdog thread processes a linked list of pending timeouts, sorted in the order to be
     * triggered. This class synchronizes on AsyncTimeout.class. This lock guards the queue.
     *
     * Head's 'next' points to the first element of the linked list. The first element is the next
     * node to time out, or null if the queue is empty. The head is null until the watchdog thread
     * is started and also after being idle for [AsyncTimeout.IDLE_TIMEOUT_MILLIS].
     */

    private fun insertIntoQueue(node: AsyncTimeout, timeoutNanos: Long, hasDeadline: Boolean) {
      // Start the watchdog thread and create the head node when the first timeout is scheduled.
      if (dataStructure.head == null) {
        dataStructure.head = AsyncTimeout()
        Watchdog().start()
      }

      val now = System.nanoTime()
      if (timeoutNanos != 0L && hasDeadline) {
        // Compute the earliest event; either timeout or deadline. Because nanoTime can wrap
        // around, minOf() is undefined for absolute values, but meaningful for relative ones.
        node.timeoutAt = now + minOf(timeoutNanos, node.deadlineNanoTime() - now)
      } else if (timeoutNanos != 0L) {
        node.timeoutAt = now + timeoutNanos
      } else if (hasDeadline) {
        node.timeoutAt = node.deadlineNanoTime()
      } else {
        throw AssertionError()
      }

      // Insert the node into the queue.
      dataStructure.insertIntoQueue(node)
      if (node.index == 1) {
        // Wake up the watchdog when inserting at the front.
        condition.signal()
      }
    }

    /**
     * Removes and returns the node at the head of the list, waiting for it to time out if
     * necessary. This returns [head] if there was no node at the head of the list when starting,
     * and there continues to be no node after waiting [IDLE_TIMEOUT_NANOS]. It returns null if a
     * new node was inserted while waiting. Otherwise, this returns the node being waited on that
     * has been removed.
     */
    @Throws(InterruptedException::class)
    fun awaitTimeout(): AsyncTimeout? {
      // Get the next eligible node.
      val node = dataStructure.first()

      // The queue is empty. Wait until either something is enqueued or the idle timeout elapses.
      if (node == null) {
        val startNanos = System.nanoTime()
        condition.await(IDLE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        return if (dataStructure.first() == null && System.nanoTime() - startNanos >= IDLE_TIMEOUT_NANOS) {
          dataStructure.head // The idle timeout elapsed.
        } else {
          null // The situation has changed.
        }
      }

      val waitNanos = node.remainingNanos(System.nanoTime())

      // The head of the queue hasn't timed out yet. Await that.
      if (waitNanos > 0) {
        condition.await(waitNanos, TimeUnit.NANOSECONDS)
        return null
      }

      // The head of the queue has timed out. Remove it.
      dataStructure.removeFirst(node)
      node.state = STATE_TIMED_OUT
      return node
    }

    val dataStructure = Heap()
  }
}

/**
 *  Minimum heap implementation.
 */
internal class Heap {
  internal var heapSize: Int = 0
  internal var array: Array<AsyncTimeout?> = arrayOfNulls(8)
  internal var head: AsyncTimeout? = array[0]


  fun first(): AsyncTimeout? {
    return when {
      heapSize > 0 -> array[1]
      else -> null
    }
  }


  fun insertIntoQueue(node: AsyncTimeout) {
    val newHeapSize = heapSize + 1
    heapSize = newHeapSize
    if (newHeapSize == array.size) {
      val doubledArray = arrayOfNulls<AsyncTimeout?>(newHeapSize * 2)
      array.copyInto(doubledArray,)
      array = doubledArray
    }

    heapifyUp(newHeapSize, node)
  }


  fun removeFromQueue(node: AsyncTimeout) {
    require(node.index != -1)
    val originalHeapSize = this@Heap.heapSize

    // Remove the node.
    val removedIndex = node.index
    val last = array[originalHeapSize]!!
    node.index = -1
    array[originalHeapSize] = null
    this.heapSize--

    // Fix the heap to fill the vacated index.
    if (removedIndex == originalHeapSize) return
    val nodeCompareToLast = node.compareTo(last)
    when {
      // Put last in the removed node's spot.
      nodeCompareToLast == 0 -> {
        array[removedIndex] = last
        last.index = removedIndex
      }
      nodeCompareToLast < 0 -> heapifyDown(removedIndex, last)
      else -> heapifyUp(removedIndex, last)
    }
  }


  fun removeFirst(first: AsyncTimeout) {
    removeFromQueue(first)
  }

  /**
   * Put [node] in the right position in the heap by moving [node] up the heap.
   *
   * When this is done it'll put something in [vacantIndex], and [node] somewhere in the heap.
   *
   * @param vacantIndex an index in the heap that is empty.
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
   * Put [node] in the right position in the heap by moving [node] down the heap.
   *
   * When this is done it'll put something in [vacantIndex], and [node] somewhere in the heap.
   *
   * @param vacantIndex an index in the heap that is empty.
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
        rightIndex <= heapSize -> {
          val leftNode = array[leftIndex]!!
          val rightNode = array[rightIndex]!!
          when {
            leftNode < rightNode -> leftNode
            else -> rightNode
          }
        }
        leftIndex <= heapSize -> {
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

}

@Suppress("NOTHING_TO_INLINE")
inline operator fun AsyncTimeout.compareTo(other: AsyncTimeout): Int {
  val a = timeoutNanos()
  val b = other.timeoutNanos()
  return 0L.compareTo(b - a)
}
