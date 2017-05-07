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
import okio.Sink;
import okio.Source;

public final class Behavior {
  public interface Callback {
    void call(Behavior behavior) throws IOException;
  }

  private volatile Callback beforeFlushCallback;
  private volatile Callback afterFlushCallback;
  private volatile Callback beforeCloseCallback;
  private volatile Callback afterCloseCallback;
  private final ByteRate byteRate = new ByteRate();

  /**
   * A {@linkplain Callback callback} to be invoked before a call to {@code flush} on {@link Sink}.
   * If the callback throws an exception, the underlying {@code flush()} method will not be called
   * nor {@link #setAfterFlushCallback(Callback)}.
   */
  public void setBeforeFlushCallback(Callback callback) {
    beforeFlushCallback = callback;
  }

  /**
   * A {@linkplain Callback callback} to be invoked after a call to {@code flush} on {@link Sink}.
   * If a {@link #setBeforeFlushCallback(Callback)} or the underlying {@code flush()} method
   * throws an exception this callback will not run.
   */
  public void setAfterFlushCallback(Callback callback) {
    afterFlushCallback = callback;
  }

  /**
   * A {@linkplain Callback callback} to be invoked before a call to {@code close} on {@link Sink}
   * and {@link Source}. If the callback throws an exception, the underlying {@code close()} method
   * will not be called nor {@link #setAfterCloseCallback(Callback)}.
   */
  public void setBeforeCloseCallback(Callback callback) {
    beforeCloseCallback = callback;
  }

  /**
   * A {@linkplain Callback callback} to be invoked after a call to {@code close} on {@link Sink}
   * and {@link Source}. If a {@link #setBeforeCloseCallback(Callback)} or the underlying
   * {@code close()} method throws ane exception this callback will not run.
   */
  public void setAfterCloseCallback(Callback callback) {
    afterCloseCallback = callback;
  }

  /**
   * The rate of byte transfer to be applied to {@link Sink#write(Buffer, long)} and {@link
   * Source#read(Buffer, long)}
   */
  public ByteRate byteRate() {
    return byteRate;
  }

  void beforeFlush() throws IOException {
    Callback beforeFlushCallback = this.beforeFlushCallback;
    if (beforeFlushCallback != null) {
      beforeFlushCallback.call(this);
    }
  }

  void afterFlush() throws IOException {
    Callback afterFlushCallback = this.afterFlushCallback;
    if (afterFlushCallback != null) {
      afterFlushCallback.call(this);
    }
  }

  public void beforeClose() throws IOException {
    Callback beforeCloseCallback = this.beforeCloseCallback;
    if (beforeCloseCallback != null) {
      beforeCloseCallback.call(this);
    }
  }

  void afterClose() throws IOException {
    Callback afterCloseCallback = this.afterCloseCallback;
    if (afterCloseCallback != null) {
      afterCloseCallback.call(this);
    }
  }
}
