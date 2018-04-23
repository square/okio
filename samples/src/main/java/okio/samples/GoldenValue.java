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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import okio.Buffer;
import okio.ByteString;

public final class GoldenValue {
  public void run() throws Exception {
    Point point = new Point(8.0, 15.0);
    ByteString pointBytes = serialize(point);
    System.out.println(pointBytes.base64());

    ByteString goldenBytes = ByteString.decodeBase64("rO0ABXNyAB5va2lvLnNhbXBsZ"
        + "XMuR29sZGVuVmFsdWUkUG9pbnTdUW8rMji1IwIAAkQAAXhEAAF5eHBAIAAAAAAAAEAuA"
        + "AAAAAAA");
    Point decoded = (Point) deserialize(goldenBytes);
    assertEquals(point, decoded);
  }

  private ByteString serialize(Object o) throws IOException {
    Buffer buffer = new Buffer();
    try (ObjectOutputStream objectOut = new ObjectOutputStream(buffer.outputStream())) {
      objectOut.writeObject(o);
    }
    return buffer.readByteString();
  }

  private Object deserialize(ByteString byteString) throws IOException, ClassNotFoundException {
    Buffer buffer = new Buffer();
    buffer.write(byteString);
    try (ObjectInputStream objectIn = new ObjectInputStream(buffer.inputStream())) {
      Object result = objectIn.readObject();
      if (objectIn.read() != -1) throw new IOException("Unconsumed bytes in stream");
      return result;
    }
  }

  static final class Point implements Serializable {
    double x;
    double y;

    Point(double x, double y) {
      this.x = x;
      this.y = y;
    }
  }

  private void assertEquals(Point a, Point b) {
    if (a.x != b.x || a.y != b.y) throw new AssertionError();
  }

  public static void main(String... args) throws Exception {
    new GoldenValue().run();
  }
}
