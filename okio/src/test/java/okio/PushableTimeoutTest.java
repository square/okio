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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PushableTimeoutTest {
  @Test public void originalEmptyTimeoutIsIgnored() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();
    target.timeout(0L, TimeUnit.NANOSECONDS);
    pushable.timeout(250L, TimeUnit.NANOSECONDS);

    pushable.push(target);
    assertEquals(250L, target.timeoutNanos());

    pushable.pop();
    assertEquals(0L, target.timeoutNanos());
  }

  @Test public void otherEmptyTimeoutIsIgnored() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();
    target.timeout(250L, TimeUnit.NANOSECONDS);
    pushable.timeout(0L, TimeUnit.NANOSECONDS);

    pushable.push(target);
    assertEquals(250L, target.timeoutNanos());

    pushable.pop();
    assertEquals(250L, target.timeoutNanos());
  }

  @Test public void originalTimeoutPrevailsWhenSmaller() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();
    target.timeout(250L, TimeUnit.NANOSECONDS);
    pushable.timeout(500L, TimeUnit.NANOSECONDS);

    pushable.push(target);
    assertEquals(250L, target.timeoutNanos());

    pushable.pop();
    assertEquals(250L, target.timeoutNanos());
  }

  @Test public void originalTimeoutCollapsesWhenBigger() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();
    target.timeout(500L, TimeUnit.NANOSECONDS);
    pushable.timeout(250L, TimeUnit.NANOSECONDS);

    pushable.push(target);
    assertEquals(250L, target.timeoutNanos());

    pushable.pop();
    assertEquals(500L, target.timeoutNanos());
  }

  @Test public void originalDeadlinePrevailsWhenSmaller() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();
    target.deadlineNanoTime(250L);
    pushable.deadlineNanoTime(500L);

    pushable.push(target);
    assertEquals(250L, target.deadlineNanoTime());

    pushable.pop();
    assertEquals(250L, target.deadlineNanoTime());
  }

  @Test public void originalDeadlineCollapsesWhenBigger() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();
    target.deadlineNanoTime(500L);
    pushable.deadlineNanoTime(250L);

    pushable.push(target);
    assertEquals(250L, target.deadlineNanoTime());

    pushable.pop();
    assertEquals(500L, target.deadlineNanoTime());
  }

  @Test public void originalDeadlineAppliesWhenOtherHasNoDeadline() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();
    target.deadlineNanoTime(250L);

    pushable.push(target);
    assertEquals(250L, target.deadlineNanoTime());

    pushable.pop();
    assertTrue(target.hasDeadline());
    assertEquals(250L, target.deadlineNanoTime());
  }

  @Test public void otherDeadlineAppliesWhenOriginalHasNoDeadline() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();
    pushable.deadlineNanoTime(250L);

    pushable.push(target);
    assertEquals(250L, target.deadlineNanoTime());

    pushable.pop();
    assertFalse(target.hasDeadline());
  }

  @Test public void noDeadlineWhenEitherHaveNone() {
    PushableTimeout pushable = new PushableTimeout();
    Timeout target = new Timeout();

    pushable.push(target);
    assertFalse(target.hasDeadline());

    pushable.pop();
    assertFalse(target.hasDeadline());
  }
}
