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

internal const val REPLACEMENT_BYTE: Byte = '?'.toByte()
internal const val REPLACEMENT_CHARACTER: Char = '\ufffd'
internal const val REPLACEMENT_CODE_POINT: Int = REPLACEMENT_CHARACTER.toInt()

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun isIsoControl(codePoint: Int): Boolean =
  (codePoint in 0x00..0x1F) || (codePoint in 0x7F..0x9F)

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun isUtf8Continuation(byte: Byte): Boolean {
  // 0b10xxxxxx
  return byte and 0xc0 == 0x80
}

// TODO combine with Buffer.writeUtf8?
// TODO combine with Buffer.writeUtf8CodePoint?
internal inline fun String.processUtf8Bytes(
  beginIndex: Int,
  endIndex: Int,
  yield: (Byte) -> Unit
) {
  // Transcode a UTF-16 String to UTF-8 bytes.
  var index = beginIndex
  while (index < endIndex) {
    val c = this[index]

    when {
      c < '\u0080' -> {
        // Emit a 7-bit character with 1 byte.
        yield(c.toByte()) // 0xxxxxxx
        index++

        // Assume there is going to be more ASCII
        while (index < endIndex && this[index] < '\u0080') {
          yield(this[index++].toByte())
        }
      }

      c < '\u0800' -> {
        // Emit a 11-bit character with 2 bytes.
        /* ktlint-disable no-multi-spaces */
        yield((c.toInt() shr 6          or 0xc0).toByte()) // 110xxxxx
        yield((c.toInt()       and 0x3f or 0x80).toByte()) // 10xxxxxx
        /* ktlint-enable no-multi-spaces */
        index++
      }

      c !in '\ud800'..'\udfff' -> {
        // Emit a 16-bit character with 3 bytes.
        /* ktlint-disable no-multi-spaces */
        yield((c.toInt() shr 12          or 0xe0).toByte()) // 1110xxxx
        yield((c.toInt() shr  6 and 0x3f or 0x80).toByte()) // 10xxxxxx
        yield((c.toInt()        and 0x3f or 0x80).toByte()) // 10xxxxxx
        /* ktlint-enable no-multi-spaces */
        index++
      }

      else -> {
        // c is a surrogate. Make sure it is a high surrogate & that its successor is a low
        // surrogate. If not, the UTF-16 is invalid, in which case we emit a replacement
        // byte.
        if (c > '\udbff' ||
            endIndex <= index + 1 ||
            this[index + 1] !in '\udc00'..'\udfff') {
          yield(REPLACEMENT_BYTE)
          index++
        } else {
          // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
          // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
          // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
          val codePoint = (((c.toInt() shl 10) + this[index + 1].toInt()) +
            (0x010000 - (0xd800 shl 10) - 0xdc00))

          // Emit a 21-bit character with 4 bytes.
          /* ktlint-disable no-multi-spaces */
          yield((codePoint shr 18          or 0xf0).toByte()) // 11110xxx
          yield((codePoint shr 12 and 0x3f or 0x80).toByte()) // 10xxxxxx
          yield((codePoint shr 6  and 0x3f or 0x80).toByte()) // 10xxyyyy
          yield((codePoint        and 0x3f or 0x80).toByte()) // 10yyyyyy
          /* ktlint-enable no-multi-spaces */
          index += 2
        }
      }
    }
  }
}

// TODO combine with Buffer.readUtf8CodePoint?
internal inline fun ByteArray.processUtf8CodePoints(
  beginIndex: Int,
  endIndex: Int,
  yield: (Int) -> Unit
) {
  var index = beginIndex
  while (index < endIndex) {
    val b0 = this[index]
    when {
      b0 >= 0 -> {
        // 0b0xxxxxxx
        yield(b0.toInt())
        index++

        // Assume there is going to be more ASCII
        while (index < endIndex && this[index] >= 0) {
          yield(this[index++].toInt())
        }
      }
      b0 shr 5 == -2 -> {
        // 0b110xxxxx
        index += process2Utf8Bytes(index, endIndex) { yield(it) }
      }
      b0 shr 4 == -2 -> {
        // 0b1110xxxx
        index += process3Utf8Bytes(index, endIndex) { yield(it) }
      }
      b0 shr 3 == -2 -> {
        // 0b11110xxx
        index += process4Utf8Bytes(index, endIndex) { yield(it) }
      }
      else -> {
        // 0b10xxxxxx - Unexpected continuation
        // 0b111111xxx - Unknown encoding
        yield(REPLACEMENT_CODE_POINT)
        index++
      }
    }
  }
}

// TODO combine with Buffer.readUtf8?
internal inline fun ByteArray.processUtf8Chars(
  beginIndex: Int,
  endIndex: Int,
  yield: (Char) -> Unit
) {
  var index = beginIndex
  while (index < endIndex) {
    val b0 = this[index]
    when {
      b0 >= 0 -> {
        // 0b0xxxxxxx
        yield(b0.toChar())
        index++

        // Assume there is going to be more ASCII
        // This is almost double the performance of the outer loop
        while (index < endIndex && this[index] >= 0) {
          yield(this[index++].toChar())
        }
      }
      b0 shr 5 == -2 -> {
        // 0b110xxxxx
        index += process2Utf8Bytes(index, endIndex) { yield(it.toChar()) }
      }
      b0 shr 4 == -2 -> {
        // 0b1110xxxx
        index += process3Utf8Bytes(index, endIndex) { yield(it.toChar()) }
      }
      b0 shr 3 == -2 -> {
        // 0b11110xxx
        index += process4Utf8Bytes(index, endIndex) { codePoint ->
          if (codePoint != REPLACEMENT_CODE_POINT) {
            // Unicode code point:    00010000000000000000 + xxxxxxxxxxyyyyyyyyyy (21 bits)
            // UTF-16 high surrogate: 110110xxxxxxxxxx (10 bits)
            // UTF-16 low surrogate:  110111yyyyyyyyyy (10 bits)
            /* ktlint-disable no-multi-spaces */
            yield(((codePoint ushr 10   ) + (0xd800 - (0x010000 ushr 10))).toChar())
            yield(((codePoint and 0x03ff) + (0xdc00                     )).toChar())
            /* ktlint-enable no-multi-spaces */
          } else {
            yield(REPLACEMENT_CHARACTER)
          }
        }
      }
      else -> {
        // 0b10xxxxxx - Unexpected continuation
        // 0b111111xxx - Unknown encoding
        yield(REPLACEMENT_CHARACTER)
        index++
      }
    }
  }
}

/* ktlint-disable indent */
internal val mask2 =
    ((0xc0.toByte() shl 6) xor
     (0x80.toByte().toInt()))
/* ktlint-enable indent */

internal inline fun ByteArray.process2Utf8Bytes(
  beginIndex: Int,
  endIndex: Int,
  yield: (Int) -> Unit
): Int {
  if (endIndex <= beginIndex + 1) {
    yield(REPLACEMENT_CODE_POINT)
    // Only 1 byte remaining - underflow
    return 1
  }

  val b0 = this[beginIndex]
  val b1 = this[beginIndex + 1]
  if (!isUtf8Continuation(b1)) {
    yield(REPLACEMENT_CODE_POINT)
    return 1
  }

  val codePoint =
    (mask2
        xor (b1.toInt())
        xor (b0.toInt() shl 6))

  when {
    codePoint < 0x80 -> {
      yield(REPLACEMENT_CODE_POINT) // Reject overlong code points.
    }
    else -> {
      yield(codePoint)
    }
  }
  return 2
}

/* ktlint-disable indent */
internal val mask3 =
    ((0xe0.toByte() shl 12) xor
     (0x80.toByte() shl 6) xor
     (0x80.toByte().toInt()))
/* ktlint-enable indent */

internal inline fun ByteArray.process3Utf8Bytes(
  beginIndex: Int,
  endIndex: Int,
  yield: (Int) -> Unit
): Int {
  if (endIndex <= beginIndex + 2) {
    // At least 2 bytes remaining
    yield(REPLACEMENT_CODE_POINT)
    if (endIndex <= beginIndex + 1 || !isUtf8Continuation(this[beginIndex + 1])) {
      // Only 1 byte remaining - underflow
      // Or 2nd byte is not a continuation - malformed
      return 1
    } else {
      // Only 2 bytes remaining - underflow
      return 2
    }
  }

  val b0 = this[beginIndex]
  val b1 = this[beginIndex + 1]
  if (!isUtf8Continuation(b1)) {
    yield(REPLACEMENT_CODE_POINT)
    return 1
  }
  val b2 = this[beginIndex + 2]
  if (!isUtf8Continuation(b2)) {
    yield(REPLACEMENT_CODE_POINT)
    return 2
  }

  val codePoint =
    (mask3
        xor (b2.toInt())
        xor (b1.toInt() shl 6)
        xor (b0.toInt() shl 12))

  when {
    codePoint < 0x800 -> {
      yield(REPLACEMENT_CODE_POINT) // Reject overlong code points.
    }
    codePoint in 0xd800..0xdfff -> {
      yield(REPLACEMENT_CODE_POINT) // Reject partial surrogates.
    }
    else -> {
      yield(codePoint)
    }
  }
  return 3
}

/* ktlint-disable indent */
internal val mask4 =
    ((0xf0.toByte() shl 18) xor
     (0x80.toByte() shl 12) xor
     (0x80.toByte() shl 6) xor
     (0x80.toByte().toInt()))
/* ktlint-enable indent */

internal inline fun ByteArray.process4Utf8Bytes(
  beginIndex: Int,
  endIndex: Int,
  yield: (Int) -> Unit
): Int {
  if (endIndex <= beginIndex + 3) {
    // At least 3 bytes remaining
    yield(REPLACEMENT_CODE_POINT)
    if (endIndex <= beginIndex + 1 || !isUtf8Continuation(this[beginIndex + 1])) {
      // Only 1 byte remaining - underflow
      // Or 2nd byte is not a continuation - malformed
      return 1
    } else if (endIndex <= beginIndex + 2 || !isUtf8Continuation(this[beginIndex + 2])) {
      // Only 2 bytes remaining - underflow
      // Or 3rd byte is not a continuation - malformed
      return 2
    } else {
      // Only 3 bytes remaining - underflow
      return 3
    }
  }

  val b0 = this[beginIndex]
  val b1 = this[beginIndex + 1]
  if (!isUtf8Continuation(b1)) {
    yield(REPLACEMENT_CODE_POINT)
    return 1
  }
  val b2 = this[beginIndex + 2]
  if (!isUtf8Continuation(b2)) {
    yield(REPLACEMENT_CODE_POINT)
    return 2
  }
  val b3 = this[beginIndex + 3]
  if (!isUtf8Continuation(b3)) {
    yield(REPLACEMENT_CODE_POINT)
    return 3
  }

  val codePoint =
    (mask4
        xor (b3.toInt())
        xor (b2.toInt() shl 6)
        xor (b1.toInt() shl 12)
        xor (b0.toInt() shl 18))

  when {
    codePoint > 0x10ffff -> {
      yield(REPLACEMENT_CODE_POINT) // Reject code points larger than the Unicode maximum.
    }
    codePoint in 0xd800..0xdfff -> {
      yield(REPLACEMENT_CODE_POINT) // Reject partial surrogates.
    }
    codePoint < 0x10000 -> {
      yield(REPLACEMENT_CODE_POINT) // Reject overlong code points.
    }
    else -> {
      yield(codePoint)
    }
  }
  return 4
}
