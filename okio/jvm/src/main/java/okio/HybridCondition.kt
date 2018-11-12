package okio

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okio.WaiterNode.WaitingContinuation
import okio.WaiterNode.WaitingThread
import kotlin.coroutines.resume

/**
 * A condition variable that works with both non-coroutine, thread-blocking code and non-blocking
 * coroutine code.
 *
 * Similar API to [java.util.concurrent.locks.Condition].
 */
internal class HybridCondition(private val lock: HybridMutex) {

  /**
   * Holds the nodes waiting to be signalled.
   * Mutation is guarded by [lock].
   */
  private val waiters = ArrayList<WaiterNode>()

  /**
   * Suspends until [signalAll] is called, or `timeout` expires.
   *
   * The caller must own [lock] when calling this method. It will temporarily unlock while waiting
   * for the signal, and then the method returns or throws it will have re-acquired the lock.
   * This means that the method may actually suspend longer than `timeout` specifies.
   */
  suspend fun waitSuspending(timeout: Timeout) {
    check(lock.isLocked) { "not locked" }

    try {
      suspendCancellableCoroutine<Unit> { continuation ->
        waitSuspended(timeout, continuation)
      }
    } finally {
      withContext(NonCancellable) {
        lock.lockSuspending(Timeout.NONE)
      }
    }
  }

  /**
   * Blocks until [signalAll] is called or `timeout` expires.
   *
   * The caller must own [lock] when calling this method. It will temporarily unlock while waiting
   * for the signal, and then the method returns or throws it will have re-acquired the lock.
   * This means that the method may actually suspend longer than `timeout` specifies.
   */
  fun waitBlocking(timeout: Timeout) {
    check(lock.isLocked) { "not locked" }

    val node = WaitingThread(Thread.currentThread())
    waiters += node
    lock.unlock()

    try {
      synchronized(node) {
        while (!node.notified) {
          timeout.waitUntilNotified(node)
        }
      }
    } finally {
      lock.lockBlocking(Timeout.NONE)
    }
  }

  /**
   * Wakes up all threads blocked on [waitBlocking] and all coroutines suspended on
   * [waitSuspending]. Those threads and coroutines will then immediately attempt to acquire
   * [lock]. This method does not release [lock].
   *
   * The caller must own [lock] when calling this method.
   */
  fun signalAll() {
    check(lock.isLocked) { "not locked" }

    for (waiter in waiters) {
      when (waiter) {
        is WaitingContinuation -> {
          // The finally block in waitSuspending will handle re-acquiring the lock.
          waiter.continuation.resume(Unit)
        }
        is WaitingThread -> {
          synchronized(waiter) {
            waiter.notified = true
            (waiter as Object).notifyAll()
          }
        }
      }
    }
    waiters.clear()
  }

  private fun waitSuspended(
    timeout: Timeout,
    continuation: CancellableContinuation<Unit>
  ) {
    timeout.cancelAfterTimeoutOrDeadline(continuation)
    val node = WaitingContinuation(continuation)
    waiters += node
    // We can't just remove the node from the list on cancelation because we don't
    // have the lock so it's not safe.
    lock.unlock()
  }
}
