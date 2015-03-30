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
package okio.test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import okio.Buffer;
import okio.ForwardingSource;
import okio.Okio;
import okio.Source;
import okio.test.Behavior.Callback;
import org.junit.Test;
import org.mockito.InOrder;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public final class BehaviorSourceTest {
  @Test public void byteRateSlowsSource() throws IOException {
    RecordingSource source = new RecordingSource(30);
    BehaviorSource behaviorSource = new BehaviorSource(source);
    Behavior behavior = behaviorSource.behavior();
    ByteRate byteRate = behavior.byteRate();
    byteRate.setByteCount(10);
    byteRate.setPeriod(100, MILLISECONDS);

    long startNanos = System.nanoTime();
    Okio.buffer(behaviorSource).readUtf8();
    long tookNanos = System.nanoTime() - startNanos;

    assertTrue(tookNanos >= MILLISECONDS.toNanos(300));
    source.assertLog("read(10)", "read(10)", "read(10)");
  }

  @Test public void slowSourceIsNotSlowedFurther() throws IOException {
    RecordingSource source = new RecordingSource(30);

    // A source whose reads take 2 seconds to complete.
    ForwardingSource slowSource = new ForwardingSource(source) {
      @Override public long read(Buffer sink, long byteCount) throws IOException {
        try {
          Thread.sleep(200L);
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
        return super.read(sink, byteCount);
      }
    };

    BehaviorSource behaviorSource = new BehaviorSource(slowSource);
    Behavior behavior = behaviorSource.behavior();
    ByteRate byteRate = behavior.byteRate();
    byteRate.setByteCount(10);
    byteRate.setPeriod(100, MILLISECONDS);

    long startNanos = System.nanoTime();
    Okio.buffer(behaviorSource).readUtf8();
    long tookNanos = System.nanoTime() - startNanos;

    assertTrue(tookNanos >= MILLISECONDS.toNanos(600));
    source.assertLog("read(10)", "read(10)", "read(10)");
  }

  @Test public void byteRateCanChangeOverTime() throws IOException {
    RecordingSource source = new RecordingSource(30);

    final AtomicReference<ByteRate> byteRateRef = new AtomicReference<>();
    ForwardingSource slowSource = new ForwardingSource(source) {
      @Override public long read(Buffer source, long byteCount) throws IOException {
        long read = super.read(source, byteCount);

        // Make the next read 5 times slower and twice the count.
        ByteRate byteRate = byteRateRef.get();
        byteRate.setByteCount(byteRate.byteCount() * 2);
        byteRate.setPeriod(byteRate.period(MILLISECONDS) * 5, MILLISECONDS);

        return read;
      }
    };

    BehaviorSource behaviorSource = new BehaviorSource(slowSource);
    Behavior behavior = behaviorSource.behavior();
    ByteRate byteRate = behavior.byteRate();
    byteRate.setByteCount(10);
    byteRate.setPeriod(100, MILLISECONDS);
    byteRateRef.set(byteRate);

    long startNanos = System.nanoTime();
    Okio.buffer(behaviorSource).readUtf8();
    long tookNanos = System.nanoTime() - startNanos;

    assertTrue(tookNanos >= MILLISECONDS.toNanos(600));
    source.assertLog("read(10)", "read(20)");
  }

  @Test public void closeCallsBehavior() throws IOException {
    Source source = mock(Source.class);
    BehaviorSource behaviorSource = new BehaviorSource(source);
    Callback before = mock(Callback.class);
    Callback after = mock(Callback.class);

    Behavior behavior = behaviorSource.behavior();
    behavior.setBeforeCloseCallback(before);
    behavior.setAfterCloseCallback(after);

    behaviorSource.close();

    InOrder order = inOrder(before, source, after);
    order.verify(before).call(behavior);
    order.verify(source).close();
    order.verify(after).call(behavior);
    order.verifyNoMoreInteractions();
  }
}
