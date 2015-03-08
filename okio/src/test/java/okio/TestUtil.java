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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

final class TestUtil {
  private TestUtil() {
  }

  static void assertByteArraysEquals(byte[] a, byte[] b) {
    assertEquals(Arrays.toString(a), Arrays.toString(b));
  }

  static void assertByteArrayEquals(String expectedUtf8, byte[] b) {
    assertEquals(expectedUtf8, new String(b, Util.UTF_8));
  }

  static ByteString randomBytes(int length) {
    Random random = new Random(0);
    byte[] randomBytes = new byte[length];
    random.nextBytes(randomBytes);
    return ByteString.of(randomBytes);
  }

  static String repeat(char c, int count) {
    char[] array = new char[count];
    Arrays.fill(array, c);
    return new String(array);
  }

  public static void assertEquivalent(ByteString b1, ByteString b2) {
    // Equals.
    assertTrue(b1.equals(b2));
    assertTrue(b1.equals(b1));
    assertTrue(b2.equals(b1));

    // Hash code.
    assertEquals(b1.hashCode(), b2.hashCode());
    assertEquals(b1.hashCode(), b1.hashCode());
    assertEquals(b1.toString(), b2.toString());

    // Content.
    assertEquals(b1.size(), b2.size());
    byte[] b2Bytes = b2.toByteArray();
    for (int i = 0; i < b2Bytes.length; i++) {
      byte b = b2Bytes[i];
      assertEquals(b, b1.getByte(i));
    }
    assertByteArraysEquals(b1.toByteArray(), b2Bytes);

    // Doesn't equal a different byte string.
    assertFalse(b1.equals(null));
    assertFalse(b1.equals(new Object()));
    if (b2Bytes.length > 0) {
      byte[] b3Bytes = b2Bytes.clone();
      b3Bytes[b3Bytes.length - 1]++;
      ByteString b3 = new ByteString(b3Bytes);
      assertFalse(b1.equals(b3));
      assertFalse(b1.hashCode() == b3.hashCode());
    } else {
      ByteString b3 = ByteString.encodeUtf8("a");
      assertFalse(b1.equals(b3));
      assertFalse(b1.hashCode() == b3.hashCode());
    }
  }

  public static void assertEquivalent(Buffer b1, Buffer b2) {
    // Equals.
    assertTrue(b1.equals(b2));
    assertTrue(b1.equals(b1));
    assertTrue(b2.equals(b1));

    // Hash code.
    assertEquals(b1.hashCode(), b2.hashCode());
    assertEquals(b1.hashCode(), b1.hashCode());
    assertEquals(b1.toString(), b2.toString());

    // Content.
    assertEquals(b1.size(), b2.size());
    Buffer buffer = new Buffer();
    b2.copyTo(buffer, 0, b2.size);
    byte[] b2Bytes = b2.readByteArray();
    for (int i = 0; i < b2Bytes.length; i++) {
      byte b = b2Bytes[i];
      assertEquals(b, b1.getByte(i));
    }

    // Doesn't equal a different buffer.
    assertFalse(b1.equals(null));
    assertFalse(b1.equals(new Object()));
    if (b2Bytes.length > 0) {
      byte[] b3Bytes = b2Bytes.clone();
      b3Bytes[b3Bytes.length - 1]++;
      Buffer b3 = new Buffer().write(b3Bytes);
      assertFalse(b1.equals(b3));
      assertFalse(b1.hashCode() == b3.hashCode());
    } else {
      Buffer b3 = new Buffer().writeUtf8("a");
      assertFalse(b1.equals(b3));
      assertFalse(b1.hashCode() == b3.hashCode());
    }
  }

  /** Serializes original to bytes, then deserializes those bytes and returns the result. */
  @SuppressWarnings("unchecked") // Assume serialization doesn't change types.
  public static <T extends Serializable> T reserialize(T original) throws Exception {
    Buffer buffer = new Buffer();
    ObjectOutputStream out = new ObjectOutputStream(buffer.outputStream());
    out.writeObject(original);
    ObjectInputStream in = new ObjectInputStream(buffer.inputStream());
    return (T) in.readObject();
  }
}
