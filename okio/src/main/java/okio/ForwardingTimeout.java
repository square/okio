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
package okio;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** A {@link Timeout} which forwards calls to another. Useful for subclassing. */
public class ForwardingTimeout extends Timeout {
  private Timeout delegate;

  public ForwardingTimeout(Timeout delegate) {
    if (delegate == null) throw new IllegalArgumentException("delegate == null");
    this.delegate = delegate;
  }

  /** {@link Timeout} instance to which this instance is currently delegating. */
  public final Timeout delegate() {
    return delegate;
  }

  public final ForwardingTimeout setDelegate(Timeout delegate) {
    if (delegate == null) throw new IllegalArgumentException("delegate == null");
    this.delegate = delegate;
    return this;
  }

  @Override public Timeout timeout(long timeout, TimeUnit unit) {
    return delegate.timeout(timeout, unit);
  }

  @Override public long timeoutNanos() {
    return delegate.timeoutNanos();
  }

  @Override public boolean hasDeadline() {
    return delegate.hasDeadline();
  }

  @Override public long deadlineNanoTime() {
    return delegate.deadlineNanoTime();
  }

  @Override public Timeout deadlineNanoTime(long deadlineNanoTime) {
    return delegate.deadlineNanoTime(deadlineNanoTime);
  }

  @Override public Timeout clearTimeout() {
    return delegate.clearTimeout();
  }

  @Override public Timeout clearDeadline() {
    return delegate.clearDeadline();
  }

  @Override public void throwIfReached() throws IOException {
    delegate.throwIfReached();
  }
}
