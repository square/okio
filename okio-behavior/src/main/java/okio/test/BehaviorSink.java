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
import okio.Buffer;
import okio.ForwardingSink;
import okio.Sink;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/** A wrapper which applies a {@link Behavior} to a {@link Sink}. */
public final class BehaviorSink extends ForwardingSink {
  private final Behavior behavior = new Behavior();

  public BehaviorSink(Sink delegate) {
    super(delegate);
  }

  public Behavior behavior() {
    return behavior;
  }

  @Override public void write(Buffer source, long byteCount) throws IOException {
    ByteRate rate = behavior.byteRate();

    for (long written = 0L; written < byteCount; ) {
      long rateByteCount = rate.byteCount();
      long periodWrite = Math.min(rateByteCount, byteCount - written);

      long readStartNanos = System.nanoTime();
      super.write(source, periodWrite);
      long readTookMs = NANOSECONDS.toMillis(System.nanoTime() - readStartNanos);

      written += periodWrite;

      long sleepMs = Math.max(0L, rate.periodForByteCount(MILLISECONDS, periodWrite) - readTookMs);
      if (sleepMs > 0L) {
        try {
          Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
      }
    }
  }

  @Override public void flush() throws IOException {
    behavior.beforeFlush();
    super.flush();
    behavior.afterFlush();
  }

  @Override public void close() throws IOException {
    behavior.beforeClose();
    super.close();
    behavior.afterClose();
  }
}
