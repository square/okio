/*
 * Copyright (C) 2015 Square, Inc.
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition

/** A [Timeout] which forwards calls to another. Useful for subclassing.  */
open class ForwardingTimeout(
  @get:JvmName("delegate")
  @set:JvmSynthetic // So .java callers get the setter that returns this.
  var delegate: Timeout,
) : Timeout() {

  // For backwards compatibility with Okio 1.x, this exists so it can return `ForwardingTimeout`.
  fun setDelegate(delegate: Timeout): ForwardingTimeout {
    this.delegate = delegate
    return this
  }

  override fun timeout(timeout: Long, unit: TimeUnit) = delegate.timeout(timeout, unit)

  override fun timeoutNanos() = delegate.timeoutNanos()

  override fun hasDeadline() = delegate.hasDeadline()

  override fun deadlineNanoTime() = delegate.deadlineNanoTime()

  override fun deadlineNanoTime(deadlineNanoTime: Long) = delegate.deadlineNanoTime(
    deadlineNanoTime,
  )

  override fun clearTimeout() = delegate.clearTimeout()

  override fun clearDeadline() = delegate.clearDeadline()

  @Throws(IOException::class)
  override fun throwIfReached() = delegate.throwIfReached()

  override fun cancel() = delegate.cancel()

  override fun awaitSignal(condition: Condition) = delegate.awaitSignal(condition)

  override fun waitUntilNotified(monitor: Any) = delegate.waitUntilNotified(monitor)
}
