/*
 * Copyright (C) 2018 Square, Inc.
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
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun <T> SelectableChannel.selectAsync(ops: Int, block: () -> T): T {
  val selectRunner = SelectRunner.instance
  // TODO: cancel the key when the coroutine is canceled?
  return suspendCoroutine { continuation ->
    selectRunner.tasks.add(SelectRunner.Task(this, ops, block, continuation))
    selectRunner.selector.wakeup()
  }
}

internal class SelectRunner internal constructor() : Thread("Okio SelectRunner") {
  init {
    isDaemon = true
  }

  val selector = Selector.open()
  val tasks = ConcurrentLinkedQueue<Task<*>>()

  override fun run() {
    while (true) {
      val i = tasks.iterator()
      while (i.hasNext()) {
        val task = i.next()
        if (task.call(selector)) {
          i.remove()
        }
      }

      // Register selection keys for everything recently added.
      // TODO: enumerate the keys and decide which have timed out?
      // TODO: sample the timeout so we know when to abort?

      // Wait until something is ready.
      selector.select()
      selector.selectedKeys().clear()
    }
  }
  internal class Task<T>(
    private val channel: SelectableChannel,
    private val ops: Int,
    private val block: () -> T,
    private val continuation: Continuation<T>
  ) {
    private var key: SelectionKey? = null

    /** Returns true if this task is complete. */
    fun call(selector: Selector): Boolean {
      val key = key

      // If the task hasn't been registered yet, do that first.
      if (key == null) {
        try {
          this.key = channel.register(selector, ops, this)
        } catch (e: ClosedChannelException) {
          continuation.resumeWithException(e)
          return true
        }
        return false
      }

      // If the channel has been closed, fail the task.
      if (!key.isValid) {
        continuation.resumeWithException(IOException("canceled"))
        return true
      }

      // The channel is ready! Call the block.
      if (key.readyOps() != 0) {
        try {
          val result = block()
          continuation.resume(result)
        } catch (e: Throwable) {
          continuation.resumeWithException(e)
        }
        return true
      }

      return false
    }
  }

  companion object {
    val instance: SelectRunner by lazy {
      val result = SelectRunner()
      result.start()
      result
    }
  }
}