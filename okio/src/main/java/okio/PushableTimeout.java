/*
 * Copyright (C) 2019 Square, Inc.
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

package okio;

import java.util.concurrent.TimeUnit;

/**
 * A timeout that can apply its own state to another timeout for the duration
 * of a call. Use this when forwarding one stream to another while retaining
 * both timeouts.
 */
final class PushableTimeout extends Timeout {
  private Timeout pushed;
  private boolean originalHasDeadline;
  private long originalDeadlineNanoTime;
  private long originalTimeoutNanos;

  void push(Timeout pushed) {
    this.pushed = pushed;
    this.originalHasDeadline = pushed.hasDeadline();
    this.originalDeadlineNanoTime = originalHasDeadline ? pushed.deadlineNanoTime() : -1L;
    this.originalTimeoutNanos = pushed.timeoutNanos();

    pushed.timeout(minTimeout(originalTimeoutNanos, timeoutNanos()), TimeUnit.NANOSECONDS);

    if (originalHasDeadline && hasDeadline()) {
      pushed.deadlineNanoTime(Math.min(deadlineNanoTime(), originalDeadlineNanoTime));
    } else if (hasDeadline()) {
      pushed.deadlineNanoTime(deadlineNanoTime());
    }
  }

  void pop() {
    pushed.timeout(originalTimeoutNanos, TimeUnit.NANOSECONDS);

    if (originalHasDeadline) {
      pushed.deadlineNanoTime(originalDeadlineNanoTime);
    } else {
      pushed.clearDeadline();
    }
  }
}
