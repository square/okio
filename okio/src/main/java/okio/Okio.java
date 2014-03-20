/*
 * Copyright (C) 2014 Square, Inc.
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
import java.io.InputStream;
import java.io.OutputStream;

import static okio.Util.checkOffsetAndCount;

/** Essential APIs for working with Okio. */
public final class Okio {
  private Okio() {
  }

  /**
   * Returns a new source that buffers reads from {@code source}. The returned
   * source will perform bulk reads into its in-memory buffer. Use this wherever
   * you read a source to get an ergonomic and efficient access to data.
   */
  public static BufferedSource buffer(Source source) {
    return new RealBufferedSource(source);
  }

  /**
   * Returns a new sink that buffers writes to {@code sink}. The returned sink
   * will batch writes to {@code sink}. Use this wherever you write to a sink to
   * get an ergonomic and efficient access access to data.
   */
  public static BufferedSink buffer(Sink sink) {
    return new RealBufferedSink(sink);
  }

  /** Returns a sink that writes to {@code out}. */
  public static Sink sink(final OutputStream out) {
    return new Sink() {
      private Deadline deadline = Deadline.NONE;

      @Override public void write(Buffer source, long byteCount)
          throws IOException {
        checkOffsetAndCount(source.size, 0, byteCount);
        while (byteCount > 0) {
          deadline.throwIfReached();
          Segment head = source.head;
          int toCopy = (int) Math.min(byteCount, head.limit - head.pos);
          out.write(head.data, head.pos, toCopy);

          head.pos += toCopy;
          byteCount -= toCopy;
          source.size -= toCopy;

          if (head.pos == head.limit) {
            source.head = head.pop();
            SegmentPool.INSTANCE.recycle(head);
          }
        }
      }

      @Override public void flush() throws IOException {
        out.flush();
      }

      @Override public void close() throws IOException {
        out.close();
      }

      @Override public Sink deadline(Deadline deadline) {
        if (deadline == null) throw new IllegalArgumentException("deadline == null");
        this.deadline = deadline;
        return this;
      }

      @Override public String toString() {
        return "sink(" + out + ")";
      }
    };
  }

  /** Returns a source that reads from {@code in}. */
  public static Source source(final InputStream in) {
    return new Source() {
      private Deadline deadline = Deadline.NONE;

      @Override public long read(Buffer sink, long byteCount) throws IOException {
        if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
        deadline.throwIfReached();
        Segment tail = sink.writableSegment(1);
        int maxToCopy = (int) Math.min(byteCount, Segment.SIZE - tail.limit);
        int bytesRead = in.read(tail.data, tail.limit, maxToCopy);
        if (bytesRead == -1) return -1;
        tail.limit += bytesRead;
        sink.size += bytesRead;
        return bytesRead;
      }

      @Override public void close() throws IOException {
        in.close();
      }

      @Override public Source deadline(Deadline deadline) {
        if (deadline == null) throw new IllegalArgumentException("deadline == null");
        this.deadline = deadline;
        return this;
      }

      @Override public String toString() {
        return "source(" + in + ")";
      }
    };
  }
}
