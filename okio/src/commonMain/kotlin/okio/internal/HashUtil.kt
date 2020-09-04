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

package okio.internal

internal infix fun Long.absMod(divisor: Int): Long {
  val value = this % divisor
  return if (value < 0) divisor + value else value
}

internal fun Long.toBigEndianByteArray(): ByteArray = ByteArray(8) { index ->
  ((this shr ((7 - index) * 8)) and 0xffL).toByte()
}

internal fun Long.toLittleEndianByteArray(): ByteArray = ByteArray(8) { index ->
  ((this shr (index * 8)) and 0xffL).toByte()
}

/**
 * Left rotate an unsigned 32 bit integer by [bitCount] bits
 */
internal infix fun UInt.leftRotate(bitCount: Int): UInt {
  return (this shl bitCount) or (this shr (UInt.SIZE_BITS - bitCount))
}

/**
 * Right rotate an unsigned 32 bit integer by [bitCount] bits
 */
internal infix fun UInt.rightRotate(bitCount: Int): UInt {
  return (this shr bitCount) or (this shl (UInt.SIZE_BITS - bitCount))
}

internal infix fun ULong.rightRotate(bitCount: Int): ULong {
  return (this shr bitCount) or (this shl (ULong.SIZE_BITS - bitCount))
}

internal fun UInt.getByte(index: Int): Byte {
  require(index < 4)
  return ((this shr ((3 - index) * 8)) and 0xffu).toByte()
}

internal fun ULong.getByte(index: Int): Byte {
  require(index < 8)
  return ((this shr ((7 - index) * 8)) and 0xffUL).toByte()
}

internal fun UIntArray.toBigEndianByteArray(): ByteArray {
  return ByteArray(size * 4) { index ->
    val byteIndex = index % 4
    val hashValuesIndex = index / 4
    this[hashValuesIndex].getByte(byteIndex)
  }
}

internal fun ULongArray.toBigEndianByteArray() = ByteArray(size * 8) { index ->
  this[index / 8].getByte(index % 8)
}

internal fun bytesToBigEndianUInt(b0: Byte, b1: Byte, b2: Byte, b3: Byte) = ((b0.toUInt() and 0xffu) shl 24) or
  ((b1.toUInt() and 0xffu) shl 16) or
  ((b2.toUInt() and 0xffu) shl 8) or
  (b3.toUInt() and 0xffu)

internal fun bytesToBigEndianULong(
  b0: Byte,
  b1: Byte,
  b2: Byte,
  b3: Byte,
  b4: Byte,
  b5: Byte,
  b6: Byte,
  b7: Byte,
) = ((b0.toULong() and 0xffUL) shl 56) or
  ((b1.toULong() and 0xffUL) shl 48) or
  ((b2.toULong() and 0xffUL) shl 40) or
  ((b3.toULong() and 0xffUL) shl 32) or
  ((b4.toULong() and 0xffUL) shl 24) or
  ((b5.toULong() and 0xffUL) shl 16) or
  ((b6.toULong() and 0xffUL) shl 8) or
  (b7.toULong() and 0xffUL)

internal operator fun UIntArray.component6() = this[5]
internal operator fun UIntArray.component7() = this[6]
internal operator fun UIntArray.component8() = this[7]

internal operator fun ULongArray.component6() = this[5]
internal operator fun ULongArray.component7() = this[6]
internal operator fun ULongArray.component8() = this[7]
