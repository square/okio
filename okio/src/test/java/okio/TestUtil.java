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

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;

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
}
