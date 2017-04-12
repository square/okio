/*
 * Copyright (C) 2017 Square, Inc.
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

/**
 * Okio assumes most applications use UTF-8 exclusively, and offers optimized implementations of
 * common operations on UTF-8 strings.
 *
 * <p><table border="1" cellspacing="0" cellspacing="3">
 * <tr>
 *   <th></th>
 *   <th>{@link ByteString}</th>
 *   <th>{@link Buffer}, {@link BufferedSink}, {@link BufferedSource}</th>
 * </tr>
 * <tr>
 *   <td>Encode a string</td>
 *   <td>{@link ByteString#encodeUtf8(String)}</td>
 *   <td>{@link BufferedSink#writeUtf8(String)}</td>
 * </tr>
 * <tr>
 *   <td>Encode a code point</td>
 *   <td></td>
 *   <td>{@link BufferedSink#writeUtf8CodePoint(int)}</td>
 * </tr>
 * <tr>
 *   <td>Decode a string</td>
 *   <td>{@link ByteString#utf8()}</td>
 *   <td>{@link BufferedSource#readUtf8()}, {@link BufferedSource#readUtf8(long)}</td>
 * </tr>
 * <tr>
 *   <td>Decode a code point</td>
 *   <td></td>
 *   <td>{@link BufferedSource#readUtf8CodePoint()}</td>
 * </tr>
 * <tr>
 *   <td>Decode until the next {@code \r\n} or {@code \n}</td>
 *   <td></td>
 *   <td>{@link BufferedSource#readUtf8LineStrict()},
 *       {@link BufferedSource#readUtf8LineStrict(long)}</td>
 * </tr>
 * <tr>
 *   <td>Decode until the next {@code \r\n}, {@code \n}, or {@code EOF}</td>
 *   <td></td>
 *   <td>{@link BufferedSource#readUtf8Line()}</td>
 * </tr>
 * <tr>
 *   <td>Measure the bytes in a UTF-8 string</td>
 *   <td colspan="2">{@link Utf8#size}, {@link Utf8#size(String, int, int)}</td>
 * </tr>
 * </table>
 */
public final class Utf8 {
  private Utf8() {
  }

  /**
   * Returns the number of bytes used to encode {@code string} as UTF-8 when using {@link
   * ByteString#encodeUtf8} or {@link Buffer#writeUtf8(String)}.
   */
  public static long size(String string) {
    return size(string, 0, string.length());
  }

  /**
   * Returns the number of bytes used to encode the slice of {@code string} as UTF-8 when using
   * {@link BufferedSink#writeUtf8(String, int, int)}.
   */
  public static long size(String string, int beginIndex, int endIndex) {
    if (string == null) throw new IllegalArgumentException("string == null");
    if (beginIndex < 0) throw new IllegalArgumentException("beginIndex < 0: " + beginIndex);
    if (endIndex < beginIndex) {
      throw new IllegalArgumentException("endIndex < beginIndex: " + endIndex + " < " + beginIndex);
    }
    if (endIndex > string.length()) {
      throw new IllegalArgumentException(
          "endIndex > string.length: " + endIndex + " > " + string.length());
    }

    long result = 0;
    for (int i = beginIndex; i < endIndex;) {
      int c = string.charAt(i);

      if (c < 0x80) {
        // A 7-bit character with 1 byte.
        result++;
        i++;

      } else if (c < 0x800) {
        // An 11-bit character with 2 bytes.
        result += 2;
        i++;

      } else if (c < 0xd800 || c > 0xdfff) {
        // A 16-bit character with 3 bytes.
        result += 3;
        i++;

      } else {
        int low = i + 1 < endIndex ? string.charAt(i + 1) : 0;
        if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
          // A malformed surrogate, which yields '?'.
          result++;
          i++;

        } else {
          // A 21-bit character with 4 bytes.
          result += 4;
          i += 2;
        }
      }
    }

    return result;
  }
}
