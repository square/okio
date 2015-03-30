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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import okio.Buffer;
import okio.Source;
import okio.Timeout;

import static org.junit.Assert.assertEquals;

final class RecordingSource implements Source {
  private final List<String> log = new ArrayList<>();
  private long length;

  public RecordingSource(long length) {
    this.length = length;
  }

  public void assertLog(String... messages) {
    assertEquals(Arrays.asList(messages), log);
  }

  @Override public long read(Buffer sink, long byteCount) throws IOException {
    if (length == 0) {
      return -1;
    }

    byteCount = Math.min(byteCount, length);
    length -= byteCount;

    log.add("read(" + byteCount + ')');

    for (int i = 0; i < byteCount; i++) {
      sink.writeByte('x');
    }
    return byteCount;
  }

  @Override public Timeout timeout() {
    return Timeout.NONE;
  }

  @Override public void close() throws IOException {
    log.add("close()");
  }
}
