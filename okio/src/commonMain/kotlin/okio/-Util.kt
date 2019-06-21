/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("-Util")

package okio

import okio.internal.HEX_DIGITS
import kotlin.jvm.JvmName

internal fun checkOffsetAndCount(size: Long, offset: Long, byteCount: Long) {
  if (offset or byteCount < 0 || offset > size || size - offset < byteCount) {
    throw ArrayIndexOutOfBoundsException("size=$size offset=$offset byteCount=$byteCount")
  }
}

/* ktlint-disable no-multi-spaces indent */

internal fun Short.reverseBytes(): Short {
  val i = toInt() and 0xffff
  val reversed = (i and 0xff00 ushr 8) or
                 (i and 0x00ff  shl 8)
  return reversed.toShort()
}

internal fun Int.reverseBytes(): Int {
  return (this and -0x1000000 ushr 24) or
         (this and 0x00ff0000 ushr  8) or
         (this and 0x0000ff00  shl  8) or
         (this and 0x000000ff  shl 24)
}

internal fun Long.reverseBytes(): Long {
  return (this and -0x100000000000000L ushr 56) or
         (this and 0x00ff000000000000L ushr 40) or
         (this and 0x0000ff0000000000L ushr 24) or
         (this and 0x000000ff00000000L ushr  8) or
         (this and 0x00000000ff000000L  shl  8) or
         (this and 0x0000000000ff0000L  shl 24) or
         (this and 0x000000000000ff00L  shl 40) or
         (this and 0x00000000000000ffL  shl 56)
}

/* ktlint-enable no-multi-spaces indent */

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.shr(other: Int): Int = toInt() shr other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.shl(other: Int): Int = toInt() shl other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.and(other: Int): Int = toInt() and other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Byte.and(other: Long): Long = toLong() and other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline infix fun Int.and(other: Long): Long = toLong() and other

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun minOf(a: Long, b: Int): Long = minOf(a, b.toLong())

@Suppress("NOTHING_TO_INLINE") // Syntactic sugar.
internal inline fun minOf(a: Int, b: Long): Long = minOf(a.toLong(), b)

internal fun arrayRangeEquals(
  a: ByteArray,
  aOffset: Int,
  b: ByteArray,
  bOffset: Int,
  byteCount: Int
): Boolean {
  for (i in 0 until byteCount) {
    if (a[i + aOffset] != b[i + bOffset]) return false
  }
  return true
}

internal fun Byte.toHexString(): String {
  val result = CharArray(4)
  result[0] = '0'
  result[1] = 'x'
  result[2] = HEX_DIGITS[this shr 4 and 0xf]
  result[3] = HEX_DIGITS[this       and 0xf] // ktlint-disable no-multi-spaces
  return String(result)
}

internal fun Int.toHexString(): String {
  val result = CharArray(10)
  result[2] = HEX_DIGITS[this shr 28 and 0xf]
  result[3] = HEX_DIGITS[this shr 24 and 0xf]
  result[4] = HEX_DIGITS[this shr 20 and 0xf]
  result[5] = HEX_DIGITS[this shr 16 and 0xf]
  result[6] = HEX_DIGITS[this shr 12 and 0xf]
  result[7] = HEX_DIGITS[this shr 8  and 0xf] // ktlint-disable no-multi-spaces
  result[8] = HEX_DIGITS[this shr 4  and 0xf] // ktlint-disable no-multi-spaces
  result[9] = HEX_DIGITS[this        and 0xf] // ktlint-disable no-multi-spaces

  // Find the first non-zero index
  var i = 2
  while (i < 10) {
    if (result[i] != '0') break
    i++
  }

  // shift and insert "0x"
  i -= 2
  result[i] = '0'
  result[i + 1] = 'x'

  return String(result, i, 10 - i)
}
