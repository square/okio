package okio.internal

import kotlin.math.min

internal fun Long.toByteArray(): ByteArray = ByteArray(8) { index ->
  ((this shr ((7 - index) * 8)) and 0xffL).toByte()
}

internal fun ByteArray.chunked(chunkSize: Int): List<ByteArray> {
  val result = mutableListOf<ByteArray>()

  val lastIndex = if (size % chunkSize == 0) this.size else (size / chunkSize) + size
  for (startIndex in 0 until lastIndex step chunkSize) {
    if (startIndex > size) break
    val endIndex = min(startIndex + chunkSize - 1, size - 1)
    result.add(sliceArray(startIndex..endIndex))
  }

  return result
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

internal fun ByteArray.toUInt(): UInt {
  require(size == 4)
  var accumulator: UInt = 0.toUInt()

  forEachIndexed { index, byte ->
    accumulator = accumulator or ((byte.toUInt() and 0xff.toUInt()) shl ((3 - index) * 8))
  }

  return accumulator
}

internal fun ByteArray.toULong(): ULong {
  require(size == 8)
  var accumulator = 0.toULong()

  forEachIndexed { index, byte ->
    accumulator = accumulator or ((byte.toULong() and 0xff.toULong()) shl ((7 - index) * 8))
  }

  return accumulator
}

internal fun UInt.getByte(index: Int): Byte {
  require(index < 4)
  return ((this shr ((3 - index) * 8)) and 0xff.toUInt()).toByte()
}

internal fun ULong.getByte(index: Int): Byte {
  require(index < 8)
  return ((this shr ((7 - index) * 8)) and 0xff.toULong()).toByte()
}
