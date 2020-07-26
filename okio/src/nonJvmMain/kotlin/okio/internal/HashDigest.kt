package okio.internal

internal class HashDigest private constructor(val hashValues: UIntArray) {

  constructor(vararg hashValue: UInt) : this(hashValue)

  fun toByteArray() = ByteArray(hashValues.size * 4) { index ->
    val byteIndex = index % 4
    val hashValuesIndex = index / 4

    hashValues[hashValuesIndex].getByte(byteIndex)
  }

  operator fun get(index: Int): UInt = hashValues[index]

  operator fun component1(): UInt = hashValues[0]
  operator fun component2(): UInt = hashValues[1]
  operator fun component3(): UInt = hashValues[2]
  operator fun component4(): UInt = hashValues[3]
  operator fun component5(): UInt = hashValues[4]
  operator fun component6(): UInt = hashValues[5]
  operator fun component7(): UInt = hashValues[6]
  operator fun component8(): UInt = hashValues[7]
}