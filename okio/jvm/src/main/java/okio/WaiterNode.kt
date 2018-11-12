package okio

import kotlinx.coroutines.CancellableContinuation

internal sealed class WaiterNode {
  /**
   * Represents a coroutine that is suspended waiting to either acquire a lock or be signaled by
   * a condition.
   */
  class WaitingContinuation(
    val continuation: CancellableContinuation<Unit>
  ) : WaiterNode()

  /**
   * Represents a thread that is blocked waiting to either acquire a lock or be signaled by
   * a condition.
   */
  class WaitingThread(
    val thread: Thread
  ) : WaiterNode() {
    var notified: Boolean = false
  }
}
