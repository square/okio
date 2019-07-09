/*
 * Copyright (C) 2018 Square, Inc.
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
package okio.samples;

import java.io.IOException;
import java.util.Random;
import okio.Buffer;
import okio.ForwardingSink;
import okio.ForwardingSource;
import okio.Sink;
import okio.Source;

/**
 * Demonstrates use of the {@link Buffer.UnsafeCursor} class. While other
 * samples might demonstrate real use cases, this sample hopes to show the
 * basics of using an {@link Buffer.UnsafeCursor}:
 * <ul>
 *   <li>Efficient reuse of a single cursor instance.</li>
 *   <li>Guaranteed release of an attached cursor.</li>
 *   <li>Safe traversal of the data in a Buffer.</li>
 * </ul>
 *
 * <p>This sample implements a
 * <a href="https://en.wikipedia.org/wiki/Cipher_disk">circular cipher</a> by
 * creating a Source which will intercept all bytes written to the wire and
 * decrease their value by a specific amount. Then create a Sink which will
 * intercept all bytes read from the wire and increase their value by that same
 * specific amount. This creates an incredibly insecure way of encrypting data
 * written to the wire but demonstrates the power of the
 * {@link Buffer.UnsafeCursor} class for efficient operations on the bytes
 * being written and read.
 */
public final class Interceptors {
  public void run() throws Exception {
    final byte cipher = (byte) (new Random().nextInt(256) - 128);
    System.out.println("Cipher   : " + cipher);

    Buffer wire = new Buffer();

    // Create a Sink which will intercept and negatively rotate each byte by `cipher`
    Sink sink = new InterceptingSink(wire) {
      @Override
      protected void intercept(byte[] data, int offset, int length) {
        for (int i = offset, end = offset + length; i < end; i++) {
          data[i] -= cipher;
        }
      }
    };

    // Create a Source which will intercept and positively rotate each byte by `cipher`
    Source source = new InterceptingSource(wire) {
      @Override
      protected void intercept(byte[] data, int offset, int length) {
        for (int i = offset, end = offset + length; i < end; i++) {
          data[i] += cipher;
        }
      }
    };

    Buffer transmit = new Buffer();
    transmit.writeUtf8("This is not really a secure message");
    System.out.println("Transmit : " + transmit);

    sink.write(transmit, transmit.size());
    System.out.println("Wire     : " + wire);

    Buffer receive = new Buffer();
    source.read(receive, Long.MAX_VALUE);
    System.out.println("Receive  : " + receive);
  }

  abstract class InterceptingSource extends ForwardingSource {

    private final Buffer.UnsafeCursor cursor = new Buffer.UnsafeCursor();

    InterceptingSource(Source source) {
      super(source);
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      if (byteCount == 0) return 0;

      long result = super.read(sink, byteCount);
      if (result == -1L) return result;

      sink.readUnsafe(cursor);
      try {
        long remaining = result;
        for (int length = cursor.seek(sink.size() - result);
             remaining > 0 && length > 0;
             length = cursor.next()) {
          int toIntercept = (int) Math.min(length, remaining);
          intercept(cursor.data, cursor.start, toIntercept);
          remaining -= toIntercept;
        }
      } finally {
        cursor.close();
      }

      return result;
    }

    protected abstract void intercept(byte[] data, int offset, int length) throws IOException;
  }


  abstract class InterceptingSink extends ForwardingSink {

    private final Buffer.UnsafeCursor cursor = new Buffer.UnsafeCursor();

    InterceptingSink(Sink delegate) {
      super(delegate);
    }

    @Override
    public void write(Buffer source, long byteCount) throws IOException {
      if (byteCount < 0) throw new IllegalArgumentException("byteCount < 0: " + byteCount);
      if (source.size() < byteCount) {
        throw new IllegalArgumentException("size=" + source.size() + " byteCount=" + byteCount);
      }
      if (byteCount == 0) return;

      source.readUnsafe(cursor);
      try {
        long remaining = byteCount;
        for (int length = cursor.seek(0);
             remaining > 0 && length > 0;
             length = cursor.next()) {
          int toIntercept = (int) Math.min(length, remaining);
          intercept(cursor.data, cursor.start, toIntercept);
          remaining -= toIntercept;
        }
      } finally {
        cursor.close();
      }

      super.write(source, byteCount);
    }

    protected abstract void intercept(byte[] data, int offset, int length) throws IOException;
  }

  public static void main(String... args) throws Exception {
    new Interceptors().run();
  }
}
