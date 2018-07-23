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

/**
 * Okio assumes most applications use UTF-8 exclusively, and offers optimized implementations of
 * common operations on UTF-8 strings.
 *
 * <table border="1" cellspacing="0" cellpadding="3" summary="">
 * <tr>
 * <th></th>
 * <th>[ByteString]</th>
 * <th>[Buffer], [BufferedSink], [BufferedSource]</th>
 * </tr>
 * <tr>
 * <td>Encode a string</td>
 * <td>[ByteString.encodeUtf8]</td>
 * <td>[BufferedSink.writeUtf8]</td>
 * </tr>
 * <tr>
 * <td>Encode a code point</td>
 * <td></td>
 * <td>[BufferedSink.writeUtf8CodePoint]</td>
 * </tr>
 * <tr>
 * <td>Decode a string</td>
 * <td>[ByteString.utf8]</td>
 * <td>[BufferedSource.readUtf8], [BufferedSource.readUtf8]</td>
 * </tr>
 * <tr>
 * <td>Decode a code point</td>
 * <td></td>
 * <td>[BufferedSource.readUtf8CodePoint]</td>
 * </tr>
 * <tr>
 * <td>Decode until the next `\r\n` or `\n`</td>
 * <td></td>
 * <td>[BufferedSource.readUtf8LineStrict],
 * [BufferedSource.readUtf8LineStrict]</td>
 * </tr>
 * <tr>
 * <td>Decode until the next `\r\n`, `\n`, or `EOF`</td>
 * <td></td>
 * <td>[BufferedSource.readUtf8Line]</td>
 * </tr>
 * <tr>
 * <td>Measure the bytes in a UTF-8 string</td>
 * <td colspan="2">[Utf8.size], [Utf8.size]</td>
 * </tr>
 * </table>
 */
@file:JvmName("Utf8")

package okio

/**
 * Returns the number of bytes used to encode the slice of `string` as UTF-8 when using
 * [BufferedSink.writeUtf8].
 */
@JvmOverloads
@JvmName("size")
fun String.utf8Size(beginIndex: Int = 0, endIndex: Int = length): Long {
  require(beginIndex >= 0) { "beginIndex < 0: $beginIndex" }
  require(endIndex >= beginIndex) { "endIndex < beginIndex: $endIndex < $beginIndex" }
  require(endIndex <= length) { "endIndex > string.length: $endIndex > $length" }

  var result = 0L
  var i = beginIndex
  while (i < endIndex) {
    val c = this[i].toInt()

    if (c < 0x80) {
      // A 7-bit character with 1 byte.
      result++
      i++

    } else if (c < 0x800) {
      // An 11-bit character with 2 bytes.
      result += 2
      i++

    } else if (c < 0xd800 || c > 0xdfff) {
      // A 16-bit character with 3 bytes.
      result += 3
      i++

    } else {
      val low = if (i + 1 < endIndex) this[i + 1].toInt() else 0
      if (c > 0xdbff || low < 0xdc00 || low > 0xdfff) {
        // A malformed surrogate, which yields '?'.
        result++
        i++

      } else {
        // A 21-bit character with 4 bytes.
        result += 4
        i += 2
      }
    }
  }

  return result
}

internal const val REPLACEMENT_CHARACTER: Int = '\ufffd'.toInt()

internal fun codePointByteCount(codePoint: Int): Int = when {
  codePoint < 0x80 -> 1 // A 7-bit character with 1 byte.
  codePoint < 0x800 -> 2 // An 11-bit character with 2 bytes.
  codePoint < 0x10000 -> 3 // A 16-bit character with 3 bytes.
  else -> 4 // A 21-bit character with 4 bytes.
}

internal fun codePointCharCount(codePoint: Int): Int = when {
  codePoint < 0x10000 -> 1 // At most a 16-bit character.
  else -> 2 // A 21-bit character.
}

internal fun isIsoControl(codePoint: Int): Boolean =
  (codePoint in 0x00..0x1F) || (codePoint in 0x7F..0x9F)

// TODO: Combine with Buffer.readUtf8CodePoint if possible
internal fun ByteArray.codePointAt(index: Int): Int {
  val b0 = this[index]

  var codePoint: Int
  val byteCount: Int
  val min: Int
  when {
    b0 and 0x80 == 0 -> {
      // 0xxxxxxx.
      codePoint = b0 and 0x7f
      byteCount = 1 // 7 bits (ASCII).
      min = 0x0
    }
    b0 and 0xe0 == 0xc0 -> {
      // 0x110xxxxx
      codePoint = b0 and 0x1f
      byteCount = 2 // 11 bits (5 + 6).
      min = 0x80
    }
    b0 and 0xf0 == 0xe0 -> {
      // 0x1110xxxx
      codePoint = b0 and 0x0f
      byteCount = 3 // 16 bits (4 + 6 + 6).
      min = 0x800
    }
    b0 and 0xf8 == 0xf0 -> {
      // 0x11110xxx
      codePoint = b0 and 0x07
      byteCount = 4 // 21 bits (3 + 6 + 6 + 6).
      min = 0x10000
    }
    else -> {
      // We expected the first byte of a code point but got something else.
      return REPLACEMENT_CHARACTER
    }
  }

  if (size < index + byteCount) {
    // Not enough remaining data to interpret as a code point.
    return REPLACEMENT_CHARACTER
  }

  // Read the continuation bytes. If we encounter a non-continuation byte, the sequence thus far is
  // decoded as the replacement character.
  for (i in 1 until byteCount) {
    val b = this[index + i]
    if (b and 0xc0 == 0x80) {
      // 0x10xxxxxx
      codePoint = codePoint shl 6
      codePoint = codePoint or (b and 0x3f)
    } else {
      return REPLACEMENT_CHARACTER
    }
  }

  return when {
    codePoint > 0x10ffff -> {
      REPLACEMENT_CHARACTER // Reject code points larger than the Unicode maximum.
    }
    codePoint in 0xd800..0xdfff -> {
      REPLACEMENT_CHARACTER // Reject partial surrogates.
    }
    codePoint < min -> {
      REPLACEMENT_CHARACTER // Reject overlong code points.
    }
    else -> codePoint
  }
}
