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

import kotlin.math.min

/**
 * A convenience wrapper around [ByteArray] to allow for
 * zero copy operations.
 */
internal interface Bytes {

  val size: Int

  operator fun get(index: Int): Byte

  fun toByteArray(): ByteArray = ByteArray(size, this::get)

  companion object {
    val EMPTY: Bytes = BytesWrapper(byteArrayOf())
  }
}

private class BytesWrapper(private val source: ByteArray) : Bytes {
  override val size = source.size
  override fun get(index: Int): Byte = source[index]
}

private class BytesSlice(
  private val source: Bytes,
  private val range: IntRange
) : Bytes {

  override val size: Int = range.last - range.first + 1

  override fun get(index: Int): Byte {
    if (index >= size) {
      throw IndexOutOfBoundsException("range: $range - index $index")
    }

    return source[index + range.first]
  }
}

private class BytesUnion(
  private val left: Bytes,
  private val right: Bytes
) : Bytes {

  override val size: Int = left.size + right.size

  override fun get(index: Int): Byte = when {
    index < left.size -> left[index]
    index < size -> right[index - left.size]
    else -> throw IndexOutOfBoundsException("size: $size, index: $index")
  }
}

internal fun Bytes.slice(range: IntRange): Bytes = BytesSlice(this, range)

internal operator fun Bytes.plus(other: Bytes): Bytes = BytesUnion(this, other)

internal fun ByteArray.toBytes(): Bytes = BytesWrapper(this)

internal fun Bytes.forEachIndexed(block: (index: Int, byte: Byte) -> Unit) {
  for (i in 0 until size) { block(i, this[i]) }
}

internal fun Bytes.toBigEndianUInt(): UInt {
  require(size == 4)
  var accumulator: UInt = 0.toUInt()

  forEachIndexed { index, byte ->
    accumulator = accumulator or ((byte.toUInt() and 0xffu) shl ((3 - index) * 8))
  }

  return accumulator
}

internal fun Bytes.toLittleEndianUInt(): UInt {
  require(size == 4)
  var accumulator: UInt = 0.toUInt()

  forEachIndexed { index, byte ->
    accumulator = accumulator or ((byte.toUInt() and 0xffu) shl (index * 8))
  }

  return accumulator
}

internal fun Bytes.toULong(): ULong {
  require(size == 8)
  var accumulator = 0.toULong()

  forEachIndexed { index, byte ->
    accumulator = accumulator or ((byte.toULong() and 0xffUL) shl ((7 - index) * 8))
  }

  return accumulator
}

internal fun Bytes.chunked(chunkSize: Int): List<Bytes> {
  val result = mutableListOf<Bytes>()

  val lastIndex = if (size % chunkSize == 0) size else (size / chunkSize) + size
  for (startIndex in 0 until lastIndex step chunkSize) {
    if (startIndex > size) break
    val endIndex = min(startIndex + chunkSize - 1, size - 1)
    result.add(slice(startIndex..endIndex))
  }

  return result
}
