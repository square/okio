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
import okio.AsyncTimeout.Companion.head

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
open class AsyncTimeout(val name: String = "head") : Timeout() {
  private var state = STATE_IDLE

  override fun toString() = name

  fun print() {
    println("this: $this, left: $left, right: $right, parent: $parent")
    left?.print()
    right?.print()
    return
  }

  /** This timeouts data for the binary heap.  */
  private var next: AsyncTimeout? = null

  /** This timeouts data for the binary heap.  */
  private var parent: AsyncTimeout? = null
  private var left: AsyncTimeout? = null
  private var right: AsyncTimeout? = null
  private var size: Int = 0

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
  private fun remainingNanos(now: Long) = timeoutAt - now

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
            if (timedOut === head) {
              head = null
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
    private var head: AsyncTimeout? = null

    private fun insertIntoQueue(node: AsyncTimeout, timeoutNanos: Long, hasDeadline: Boolean) {
      // Start the watchdog thread and create the head node when the first timeout is scheduled.
      if (head == null) {
        head = AsyncTimeout()
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

      // Insert the node in sorted order.
      dataStructure.insertIntoQueue(now, node)
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
          head // The idle timeout elapsed.
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
      return node
    }

    val dataStructure = Heap
  }

  private object Heap : DataStructure {
    private var heapSize: Int = 0

    private fun compareTo(a: AsyncTimeout, b: AsyncTimeout, now: Long = System.nanoTime()): Int {
      val aRemainingNanos = a.remainingNanos(now)
      val bRemainingNanos = b.remainingNanos(now)
      return when {
        aRemainingNanos < bRemainingNanos -> -1
        aRemainingNanos > bRemainingNanos -> 1
        else -> 0
      }
    }

    // Log(N)
    private fun insertAtEnd(node: AsyncTimeout) {
      var current = head?.left // The left child of the head is the first element in the heap.
      if (current == null) {
        // The heap is empty. Insert the node as the first element.
        head!!.left = node
        node.parent = head
        heapSize = 1
        return
      }

      val newSize = heapSize + 1
      var index = newSize
      while (index > 3) {
        if (index % 2 == 0) {
          current = current!!.left
        } else {
          current = current!!.right
        }
        index /= 2
      }

      if (index % 2 == 0) {
        // Insert as the left child.
        current!!.left = node
      } else {
        // Insert as the right child.
        current!!.right = node
      }
      node.parent = current
      heapSize = newSize
    }

    // Log(N)
    private fun findLast(): AsyncTimeout? {
      // Find the last node in the heap. This is done by traversing the binary heap using the
      // binary representation of the heap size.
      if (heapSize == 0) return null

      var current = head!!.left
      var index = heapSize
      while (index > 1) {
        if (index % 2 == 0) {
          current = current!!.left
        } else {
          current = current!!.right
        }
        index /= 2
      }
      return current
    }

    private fun swap(a: AsyncTimeout, b: AsyncTimeout) {
      // Handle parent references
      a.parent?.let { if (it.left === a) it.left = b else it.right = b }
      b.parent?.let { if (it.left === b) it.left = a else it.right = a }

      // Swap parent references
      val tempParent = a.parent
      a.parent = b.parent
      b.parent = tempParent

      // Swap child references
      val tempLeft = a.left
      val tempRight = a.right
      a.left = b.left
      a.right = b.right
      b.left = tempLeft
      b.right = tempRight

      // Update children's parent references
      a.left?.let { it.parent = a }
      a.right?.let { it.parent = a }
      b.left?.let { it.parent = b }
      b.right?.let { it.parent = b }
    }

    // Log(N)
    private fun heapifyUp(node: AsyncTimeout, now: Long = System.nanoTime()) {
      // Heapify up the node. This is done by comparing the node with its parent and swapping them
      // if the node has a smaller value than its parent.
      var current = node
      while (current.parent != head && compareTo(current, current.parent!!, now) < 0) {
        // Swap current with its parent.
        // val parent = current.parent!!
        swap(current, current.parent!!)
        // current = parent
      }
    }

    // Log(N)
    private fun heapifyDown(node: AsyncTimeout, now: Long = System.nanoTime()) {
      // Heapify down the node. This is done by comparing the node with its children and swapping
      // it with the smallest child if the node has a larger value than any of its children.
      var current = node
      while (true) {
        var smallest = current
        if (current.left != null && compareTo(current.left!!, smallest, now) < 0) {
          smallest = current.left!!
        }
        if (current.right != null && compareTo(current.right!!, smallest, now) < 0) {
          smallest = current.right!!
        }
        if (smallest === current) break

        // Swap current with the smallest child.
        swap(current, smallest)
        current = smallest
      }
    }

    override fun first(): AsyncTimeout? {
      // The first node is the left child of the head.
      return head!!.left
    }

    // Log(N)
    override fun insertIntoQueue(now: Long, node: AsyncTimeout) {
      insertAtEnd(node)
      heapifyUp(node, now)
      if (node.parent === head) {
        condition.signal() // Wake up the watchdog thread if the node was inserted at the head.
      }
    }

    // Log(N)
    override fun removeFromQueue(node: AsyncTimeout) {
      check(heapSize > 0) { "Cannot remove from an empty queue" }

      val now = System.nanoTime()
      val last = findLast()!!
      swap(node, last)

      if (last.parent != null && compareTo(last, last.parent!!, now) < 0) {
        // The last node has a smaller value than its parent. Heapify up.
        heapifyUp(last, now)
      } else {
        // The last node has a larger value than its children. Heapify down.
        heapifyDown(last, now)
      }

      node.parent?.let { if (it.left === node) it.left = null else it.right = null }
      node.parent = null
      node.left = null
      node.right = null
      heapSize--
    }

    // Remove random node form the heap.
    // 1) replace random node with the last node in the heap.
    // 2) Heapify down if the replaced node has a larger value than any its children.
    // 3) Heapify up if the replaced node has a smaller value than its parent.

    // insert a node to the head.
    // Inser the node to the end of the heap.
    // Recursively Heapify up if the new node has a smaller value than its parent.

    // Removing the first node is a straightforward.
    // Replace the head of the node with the last node in the heap.
    // Recursively Heapify down if the new head has a larger value than any of its children.

    // Keep track of the size of the heap  to know the location of the last node.
    // Finding the last node should take O(log n) time. Traversal uses the binary representation of the size

    // Log(N)
    override fun removeFirst(first: AsyncTimeout) {
      // println("removing $first")
      check(heapSize > 0) { "Cannot remove from an empty queue" }

      val now = System.nanoTime()
      val last = findLast()!!
      swap(first, last)

      heapifyDown(last, now)

      first.parent?.let { if (it.left === first) it.left = null else it.right = null }
      first.parent = null
      first.left = null
      first.right = null
      first.state = STATE_TIMED_OUT
      // println("timed out: $first")
      heapSize--
    }
  }

  private object LinkedList : DataStructure {
    override fun first(): AsyncTimeout? {
      return head!!.next
    }

    override fun removeFirst(first: AsyncTimeout) {
      head!!.next = first.next
      first.next = null
      first.state = STATE_TIMED_OUT
    }

    override fun insertIntoQueue(now: Long, node: AsyncTimeout) {
      val remainingNanos = node.remainingNanos(now)
      var prev = head!!
      while (true) {
        if (prev.next == null || remainingNanos < prev.next!!.remainingNanos(now)) {
          node.next = prev.next
          prev.next = node
          if (prev === head) {
            // Wake up the watchdog when inserting at the front.
            condition.signal()
          }
          break
        }
        prev = prev.next!!
      }
    }

    /** Returns true if the timeout occurred. */
    override fun removeFromQueue(node: AsyncTimeout) {
      var prev = head
      while (prev != null) {
        if (prev.next === node) {
          prev.next = node.next
          node.next = null
          return
        }
        prev = prev.next
      }

      error("node was not found in the queue")
    }
  }

  private interface DataStructure {
    fun first(): AsyncTimeout?
    fun insertIntoQueue(now: Long, node: AsyncTimeout)
    fun removeFromQueue(node: AsyncTimeout)
    fun removeFirst(first: AsyncTimeout)
  }
}
