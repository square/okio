package okio

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Timeout.Companion.NONE
import okio.WaiterNode.WaitingContinuation
import okio.WaiterNode.WaitingThread
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

private val NO_OWNER: WaiterNode? = null

/**
 * A Mutex that can be shared between non-coroutine, thread-blocking code and non-blocking
 * coroutine code.
 *
 * Lock acquisition is first-come, first-served.
 *
 * Much simpler API than [java.util.concurrent.locks.Lock].
 */
internal class HybridMutex {

  /**
   * Holds `Thread`s and `Continuation<Unit>`s that have called one of the `lock*` methods
   * and are blocked or suspended waiting to acquire the lock.
   */
  private val waiters = ConcurrentLinkedQueue<WaiterNode>()

  /**
   * Holds the node that currently owns the lock, or [NO_OWNER] if unlocked.
   */
  private val owner = AtomicReference(NO_OWNER)

  val isLocked: Boolean get() = owner.get() != NO_OWNER

  /**
   * Suspends the coroutine until the lock can be acquired.
   *
   * Treats cancelation atomically: if this method returns, the lock has been acquired.
   * If this method throws a `CancellationException`, the lock will not have been acquired.
   *
   * @throws java.io.InterruptedIOException if the operation timed out before completing
   */
  suspend fun lockSuspending(timeout: Timeout = NONE) {
    suspendCancellableCoroutine<Unit> { continuation ->
      lockSuspended(timeout, continuation)
    }
  }

  /**
   * Blocks the thread until the lock can be acquired.
   *
   * @throws java.io.InterruptedIOException if the operation timed out before completing
   */
  fun lockBlocking(timeout: Timeout = NONE) {
    val node = WaitingThread(Thread.currentThread())
    waiters += node
    attemptProgress()

    // fast path – no contention
    if (owner.get() === node) return

    // slow path – wait until we've acquired the lock
    synchronized(node) {
      while (owner.get() !== node) {
        timeout.waitUntilNotified(node)
      }
    }
  }

  /**
   * Frees up the lock.
   */
  fun unlock() {
    val ownerNode = checkNotNull(owner.get()) { "mismatched unlock calls" }
    unlockNode(ownerNode)
  }

  private fun lockSuspended(
    timeout: Timeout = NONE,
    continuation: CancellableContinuation<Unit>
  ) {
    timeout.cancelAfterTimeoutOrDeadline(continuation)
    val node = WaitingContinuation(continuation)
    waiters += node
    continuation.invokeOnCancellation {
      // If we're canceled in the process of acquiring the lock, we might have already marked
      // the lock as owned by owner but not resumed the coroutine yet. We can safely unlock
      // here because we know the resume() call is guaranteed to immediately throw a cancellation
      // exception inside the coroutine which, according to our own contract, means the lock
      // was definitely not acquired.
      //
      // If the resume has already succeeded, this completion handler will never get called so
      // we can't accidentally unlock the lock out from under the resumed coroutine.
      unlockNode(node)

      // Note that if the coroutine was canceled while we were waiting but before officially
      // acquiring the lock, attemptProgress will detect it and move past us.
    }
    attemptProgress()
  }

  /**
   * If the lock is owned by `node`, unlocks and returns true.
   * Else returns false.
   */
  private fun unlockNode(node: WaiterNode): Boolean {
    if (owner.compareAndSet(node, NO_OWNER)) {
      // we won the unlock, resume the next waiter
      attemptProgress()
      return true
    }
    return false
  }

  /**
   * If we are unlocked, this method attempts to give the lock to the head of the waiting queue.
   * If it succeeds, it notifies the new owner to resume.
   *
   * This method is idempotent and used by both lock and unlock operations to ensure the lock
   * is always held as long as someone's waiting on it.
   */
  private fun attemptProgress() {
    val newOwner = waiters.peek() ?: return

    if (owner.compareAndSet(NO_OWNER, newOwner)) {
      // newOwner now owns the lock.
      waiters.remove(newOwner)

      // If the new owner is a continuation and was canceled before
      // the CAS, we need to unlock it here.
      // If it's canceled after this check, the completion handler will call unlock itself
      // and the resume call will be a no-op.
      if (newOwner is WaitingContinuation && newOwner.continuation.isCompleted) {
        unlockNode(newOwner)
        return
      }

      // notify newOwner that it can continue.
      when (newOwner) {
        is WaitingContinuation -> newOwner.continuation.resume(Unit)
        is WaitingThread -> {
          synchronized(newOwner) {
            (newOwner as Object).notifyAll()
          }
        }
      }
    }
  }
}

internal suspend inline fun <T> HybridMutex.withLockSuspending(
  timeout: Timeout = NONE,
  block: () -> T
): T {
  lockSuspending(timeout)
  try {
    return block()
  } finally {
    unlock()
  }
}

internal inline fun <T> HybridMutex.withLockBlocking(
  timeout: Timeout = NONE,
  block: () -> T
): T {
  lockBlocking(timeout)
  try {
    return block()
  } finally {
    unlock()
  }
}
