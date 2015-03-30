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
import okio.ForwardingSink;
import okio.Sink;
import org.junit.Test;
import org.mockito.InOrder;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

public final class BehaviorSinkTest {
  @Test public void byteRateSlowsSink() throws IOException {
    RecordingSink sink = new RecordingSink();
    BehaviorSink behaviorSink = new BehaviorSink(sink);
    Behavior behavior = behaviorSink.behavior();
    ByteRate byteRate = behavior.byteRate();
    byteRate.setByteCount(10);
    byteRate.setPeriod(100, MILLISECONDS);

    Buffer data = new Buffer().writeUtf8("This is a long string of data.");
    long startNanos = System.nanoTime();
    behaviorSink.write(data, data.size());
    long tookNanos = System.nanoTime() - startNanos;

    assertTrue(tookNanos >= MILLISECONDS.toNanos(300));
    sink.assertLog("This is a ", "long strin", "g of data.");
  }

  @Test public void slowSinkIsNotSlowedFurther() throws IOException {
    RecordingSink sink = new RecordingSink();

    // A sink whose writes take 2 seconds to complete.
    ForwardingSink slowSink = new ForwardingSink(sink) {
      @Override public void write(Buffer source, long byteCount) throws IOException {
        try {
          Thread.sleep(200L);
        } catch (InterruptedException e) {
          throw new AssertionError(e);
        }
        super.write(source, byteCount);
      }
    };

    BehaviorSink behaviorSink = new BehaviorSink(slowSink);
    Behavior behavior = behaviorSink.behavior();
    ByteRate byteRate = behavior.byteRate();
    byteRate.setByteCount(10);
    byteRate.setPeriod(100, MILLISECONDS);

    Buffer data = new Buffer().writeUtf8("This is a long string of data.");
    long startNanos = System.nanoTime();
    behaviorSink.write(data, data.size());
    long tookNanos = System.nanoTime() - startNanos;

    assertTrue(tookNanos >= MILLISECONDS.toNanos(600));
    sink.assertLog("This is a ", "long strin", "g of data.");
  }

  @Test public void byteRateCanChangeOverTime() throws IOException {
    RecordingSink sink = new RecordingSink();

    final AtomicReference<ByteRate> byteRateRef = new AtomicReference<>();
    ForwardingSink slowSink = new ForwardingSink(sink) {
      @Override public void write(Buffer source, long byteCount) throws IOException {
        super.write(source, byteCount);

        // Make the next write 5 times slower and twice the count.
        ByteRate byteRate = byteRateRef.get();
        byteRate.setByteCount(byteRate.byteCount() * 2);
        byteRate.setPeriod(byteRate.period(MILLISECONDS) * 5, MILLISECONDS);
      }
    };

    BehaviorSink behaviorSink = new BehaviorSink(slowSink);
    Behavior behavior = behaviorSink.behavior();
    ByteRate byteRate = behavior.byteRate();
    byteRate.setByteCount(10);
    byteRate.setPeriod(100, MILLISECONDS);
    byteRateRef.set(byteRate);

    Buffer data = new Buffer().writeUtf8("This is a long string of data.");
    long startNanos = System.nanoTime();
    behaviorSink.write(data, data.size());
    long tookNanos = System.nanoTime() - startNanos;

    assertTrue(tookNanos >= MILLISECONDS.toNanos(600));
    sink.assertLog("This is a ", "long string of data.");
  }

  @Test public void flushCallsBehavior() throws IOException {
    Sink sink = mock(Sink.class);
    BehaviorSink behaviorSink = new BehaviorSink(sink);
    Behavior.Callback before = mock(Behavior.Callback.class);
    Behavior.Callback after = mock(Behavior.Callback.class);

    Behavior behavior = behaviorSink.behavior();
    behavior.setBeforeFlushCallback(before);
    behavior.setAfterFlushCallback(after);

    behaviorSink.flush();

    InOrder order = inOrder(before, sink, after);
    order.verify(before).call(behavior);
    order.verify(sink).flush();
    order.verify(after).call(behavior);
    order.verifyNoMoreInteractions();
  }

  @Test public void closeCallsBehavior() throws IOException {
    Sink sink = mock(Sink.class);
    BehaviorSink behaviorSink = new BehaviorSink(sink);
    Behavior.Callback before = mock(Behavior.Callback.class);
    Behavior.Callback after = mock(Behavior.Callback.class);

    Behavior behavior = behaviorSink.behavior();
    behavior.setBeforeCloseCallback(before);
    behavior.setAfterCloseCallback(after);

    behaviorSink.close();

    InOrder order = inOrder(before, sink, after);
    order.verify(before).call(behavior);
    order.verify(sink).close();
    order.verify(after).call(behavior);
    order.verifyNoMoreInteractions();
  }
}
