package okio.internal

import okio.OkioMessageDigest
import kotlin.math.min

internal class Sha1MessageDigest : OkioMessageDigest {

  private var unprocessed: ByteArray = byteArrayOf()
  private var messageLength = 0
  private var currentDigest = Digest(
    0x67452301.toUInt(),
    0xEFCDAB89.toUInt(),
    0x98BADCFE.toUInt(),
    0x10325476.toUInt(),
    0xC3D2E1F0.toUInt()
  )

  override fun update(input: ByteArray) {
    for (chunk in (unprocessed + input).chunked(64)) {
      when (chunk.size) {
        64 -> {
          currentDigest = chunk.processChunk(currentDigest)
          messageLength += 64
        }
        else -> unprocessed = chunk
      }
    }
  }

  override fun digest(): ByteArray {
    val finalMessageLength = messageLength + unprocessed.size

    val finalMessage = listOf(
      *unprocessed.toTypedArray(),
      0x80.toByte(),
      *Array(((56 - (finalMessageLength + 1) % 64) % 64)) { 0.toByte() },
      *(finalMessageLength * 8L).toTypedByteArray()
    ).toByteArray()

    finalMessage.chunked(64).forEach { currentDigest = it.processChunk(currentDigest) }

    return currentDigest.toByteArray()
  }
}
private data class Digest(
  val first: UInt, // most significant
  val second: UInt,
  val third: UInt,
  val fourth: UInt,
  val fifth: UInt // lease significant
)

private fun ByteArray.processChunk(currentDigest: Digest): Digest {
  require(size == 64)

  val words = UIntArray(80)
  chunked(4).forEachIndexed { index, bytes ->
    words[index] = bytes.toUInt()
  }

  for (i in 16 until 80) {
    words[i] = (words[i - 3] xor words[i - 8] xor words[i - 14] xor words[i - 16]) leftRotate 1
  }

  var (a, b, c, d, e) = currentDigest

  for (i in 0 until 80) {
    val (f, k) = when (i) {
      in 0..19 -> (d xor (b and (c xor d))) to 0x5A827999.toUInt()
      in 20..39 -> (b xor c xor d) to 0x6ED9EBA1.toUInt()
      in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toUInt()
      in 60..79 -> (b xor c xor d) to 0xCA62C1D6.toUInt()
      else -> error("Index is wonky, this should never happen")
    }

    val updatedDigest = Digest(
      first = ((a leftRotate 5) + f + e + k + words[i]) and UInt.MAX_VALUE,
      second = a,
      third = b leftRotate 30,
      fourth = c,
      fifth = d
    )

    a = updatedDigest.first
    b = updatedDigest.second
    c = updatedDigest.third
    d = updatedDigest.fourth
    e = updatedDigest.fifth
  }

  return Digest(
    first = (currentDigest.first + a) and UInt.MAX_VALUE,
    second = (currentDigest.second + b) and UInt.MAX_VALUE,
    third = (currentDigest.third + c) and UInt.MAX_VALUE,
    fourth = (currentDigest.fourth + d) and UInt.MAX_VALUE,
    fifth = (currentDigest.fifth + e) and UInt.MAX_VALUE
  )
}

private fun Digest.toByteArray(): ByteArray = ByteArray(20) { index ->
  when (index) {
    in 0..3 -> first.getByte(index)
    in 4..7 -> second.getByte(index - 4)
    in 8..11 -> third.getByte(index - 8)
    in 12..15 -> fourth.getByte(index - 12)
    in 16..19 -> fifth.getByte(index - 16)
    else -> error("$index is out of bounds")
  }
}

private fun UInt.getByte(index: Int): Byte {
  require(index < 4)
  return ((this shr ((3 - index) * 8)) and 0xff.toUInt()).toByte()
}

private fun ByteArray.chunked(chunkSize: Int): List<ByteArray> {
  val result = mutableListOf<ByteArray>()

  val lastIndex = if (size % chunkSize == 0) this.size else (size / chunkSize) + size
  for (startIndex in 0 until lastIndex step chunkSize) {
    if (startIndex > size) break
    val endIndex = min(startIndex + chunkSize - 1, size - 1)
    result.add(sliceArray(startIndex..endIndex))
  }

  return result
}

private fun ByteArray.toUInt(): UInt {
  require(size == 4)
  var accumulator: UInt = 0.toUInt()

  forEachIndexed { index, byte ->
    accumulator = accumulator or ((byte.toUInt() and 0xff.toUInt()) shl ((3 - index) * 8))
  }

  return accumulator
}

private fun Long.toTypedByteArray(): Array<out Byte> = Array(8) { index ->
  ((this shr ((7 - index) * 8)) and 0xffL).toByte()
}

/**
 * Left rotate an unsigned 32 bit integer by [bitCount] bits
 */
private infix fun UInt.leftRotate(bitCount: Int): UInt {
  return ((this shl bitCount) or (this shr (UInt.SIZE_BITS - bitCount))) and UInt.MAX_VALUE
}