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
import okio.Timeout.TimeoutFolder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class TimeoutFolderTest {
  @Test public void originalEmptyTimeoutIsIgnored() {
    Timeout original = new Timeout();
    original.timeout(0L, TimeUnit.NANOSECONDS);
    Timeout other = new Timeout();
    other.timeout(250L, TimeUnit.NANOSECONDS);

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertEquals(250L, original.timeoutNanos());

    timeoutFolder.pop();
    assertEquals(0L, original.timeoutNanos());
  }

  @Test public void otherEmptyTimeoutIsIgnored() {
    Timeout original = new Timeout();
    original.timeout(250L, TimeUnit.NANOSECONDS);
    Timeout other = new Timeout();
    other.timeout(0L, TimeUnit.NANOSECONDS);

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertEquals(250L, original.timeoutNanos());

    timeoutFolder.pop();
    assertEquals(250L, original.timeoutNanos());
  }

  @Test public void originalTimeoutPrevailsWhenSmaller() {
    Timeout original = new Timeout();
    original.timeout(250L, TimeUnit.NANOSECONDS);
    Timeout other = new Timeout();
    other.timeout(500L, TimeUnit.NANOSECONDS);

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertEquals(250L, original.timeoutNanos());

    timeoutFolder.pop();
    assertEquals(250L, original.timeoutNanos());
  }

  @Test public void originalTimeoutCollapsesWhenBigger() {
    Timeout original = new Timeout();
    original.timeout(500L, TimeUnit.NANOSECONDS);
    Timeout other = new Timeout();
    other.timeout(250L, TimeUnit.NANOSECONDS);

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertEquals(250L, original.timeoutNanos());

    timeoutFolder.pop();
    assertEquals(500L, original.timeoutNanos());
  }

  @Test public void originalDeadlinePrevailsWhenSmaller() {
    Timeout original = new Timeout();
    original.deadlineNanoTime(250L);
    Timeout other = new Timeout();
    other.deadlineNanoTime(500L);

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertEquals(250L, original.deadlineNanoTime());

    timeoutFolder.pop();
    assertEquals(250L, original.deadlineNanoTime());
  }

  @Test public void originalDeadlineCollapsesWhenBigger() {
    Timeout original = new Timeout();
    original.deadlineNanoTime(500L);
    Timeout other = new Timeout();
    other.deadlineNanoTime(250L);

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertEquals(250L, original.deadlineNanoTime());

    timeoutFolder.pop();
    assertEquals(500L, original.deadlineNanoTime());
  }

  @Test public void originalDeadlineAppliesWhenOtherHasNoDeadline() {
    Timeout original = new Timeout();
    original.deadlineNanoTime(250L);
    Timeout other = new Timeout();

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertEquals(250L, original.deadlineNanoTime());

    timeoutFolder.pop();
    assertTrue(original.hasDeadline());
    assertEquals(250L, original.deadlineNanoTime());
  }

  @Test public void otherDeadlineAppliesWhenOriginalHasNoDeadline() {
    Timeout original = new Timeout();
    Timeout other = new Timeout();
    other.deadlineNanoTime(250L);

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertEquals(250L, original.deadlineNanoTime());

    timeoutFolder.pop();
    assertFalse(original.hasDeadline());
  }

  @Test public void noDeadlineWhenEitherHaveNone() {
    Timeout original = new Timeout();
    Timeout other = new Timeout();

    TimeoutFolder timeoutFolder = new TimeoutFolder(original);

    timeoutFolder.push(other);
    assertFalse(original.hasDeadline());

    timeoutFolder.pop();
    assertFalse(original.hasDeadline());
  }
}
