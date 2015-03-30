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
import okio.ForwardingSource;
import okio.Source;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/** A wrapper which applies a {@link Behavior} to a {@link Source}. */
public final class BehaviorSource extends ForwardingSource {
  private final Behavior behavior = new Behavior();

  public BehaviorSource(Source delegate) {
    super(delegate);
  }

  public Behavior behavior() {
    return behavior;
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    ByteRate rate = behavior.byteRate();

    long read = 0L;
    while (read < byteCount) {
      long rateByteCount = rate.byteCount();
      long rateSleepMs = rate.period(MILLISECONDS);

      long periodByteCount = Math.min(rateByteCount, byteCount - read);

      long readStartNanos = System.nanoTime();
      long periodRead = super.read(sink, periodByteCount);
      long readTookMs = NANOSECONDS.toMillis(System.nanoTime() - readStartNanos);

      if (periodRead == -1L) {
        return -1L;
      }
      read += periodRead;

      long sleepMs = Math.max(0L, rate.periodForByteCount(MILLISECONDS, periodRead) - readTookMs);
      if (sleepMs > 0L) {
        try {
          Thread.sleep(rateSleepMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return 0L;
        }
      }
    }

    return read;
  }

  @Override public void close() throws IOException {
    behavior.beforeClose();
    super.close();
    behavior.afterClose();
  }
}
