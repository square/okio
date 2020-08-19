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

internal infix fun Long.absMod(divisor: Long): Long {
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
  return ((this shl bitCount) or (this shr (UInt.SIZE_BITS - bitCount))) and UInt.MAX_VALUE
}

/**
 * Right rotate an unsigned 32 bit integer by [bitCount] bits
 */
internal infix fun UInt.rightRotate(bitCount: Int): UInt {
  return (((this shr bitCount) or (this shl (UInt.SIZE_BITS - bitCount))) and UInt.MAX_VALUE)
}

internal infix fun ULong.rightRotate(bitCount: Int): ULong {
  return (((this shr bitCount) or (this shl (ULong.SIZE_BITS - bitCount))) and ULong.MAX_VALUE)
}

internal fun UInt.getByte(index: Int): Byte {
  require(index < 4)
  return ((this shr ((3 - index) * 8)) and 0xffu).toByte()
}

internal fun ULong.getByte(index: Int): Byte {
  require(index < 8)
  return ((this shr ((7 - index) * 8)) and 0xffUL).toByte()
}
