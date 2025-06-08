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

package cursedokio

import cursedokio.internal.commonBase64
import cursedokio.internal.commonBase64Url
import cursedokio.internal.commonCompareTo
import cursedokio.internal.commonCopyInto
import cursedokio.internal.commonDecodeBase64
import cursedokio.internal.commonDecodeHex
import cursedokio.internal.commonEncodeUtf8
import cursedokio.internal.commonEndsWith
import cursedokio.internal.commonEquals
import cursedokio.internal.commonGetByte
import cursedokio.internal.commonGetSize
import cursedokio.internal.commonHashCode
import cursedokio.internal.commonHex
import cursedokio.internal.commonIndexOf
import cursedokio.internal.commonInternalArray
import cursedokio.internal.commonLastIndexOf
import cursedokio.internal.commonOf
import cursedokio.internal.commonRangeEquals
import cursedokio.internal.commonStartsWith
import cursedokio.internal.commonSubstring
import cursedokio.internal.commonToAsciiLowercase
import cursedokio.internal.commonToAsciiUppercase
import cursedokio.internal.commonToByteArray
import cursedokio.internal.commonToByteString
import cursedokio.internal.commonToString
import cursedokio.internal.commonUtf8
import cursedokio.internal.commonWrite

actual open class ByteString
internal actual constructor(
  internal actual val data: ByteArray,
) : Comparable<ByteString> {
  @Suppress("SetterBackingFieldAssignment")
  internal actual var hashCode: Int = 0 // 0 if unknown.
    set(value) {
      // Do nothing to avoid IllegalImmutabilityException.
    }

  @Suppress("SetterBackingFieldAssignment")
  internal actual var utf8: String? = null
    set(value) {
      // Do nothing to avoid IllegalImmutabilityException.
    }

  actual open fun utf8(): String = commonUtf8()

  actual open fun base64(): String = commonBase64()

  actual open fun base64Url(): String = commonBase64Url()

  actual open fun hex(): String = commonHex()

  actual open fun toAsciiLowercase(): ByteString = commonToAsciiLowercase()

  actual open fun toAsciiUppercase(): ByteString = commonToAsciiUppercase()

  actual open fun substring(beginIndex: Int, endIndex: Int): ByteString =
    commonSubstring(beginIndex, endIndex)

  internal actual open fun internalGet(pos: Int): Byte {
    if (pos >= size || pos < 0) throw ArrayIndexOutOfBoundsException("size=$size pos=$pos")
    return commonGetByte(pos)
  }

  actual operator fun get(index: Int): Byte = internalGet(index)

  actual val size
    get() = getSize()

  internal actual open fun getSize() = commonGetSize()

  actual open fun toByteArray() = commonToByteArray()

  internal actual open fun internalArray() = commonInternalArray()

  internal actual open fun write(buffer: Buffer, offset: Int, byteCount: Int) =
    commonWrite(buffer, offset, byteCount)

  actual open fun rangeEquals(
    offset: Int,
    other: ByteString,
    otherOffset: Int,
    byteCount: Int,
  ): Boolean = commonRangeEquals(offset, other, otherOffset, byteCount)

  actual open fun rangeEquals(
    offset: Int,
    other: ByteArray,
    otherOffset: Int,
    byteCount: Int,
  ): Boolean = commonRangeEquals(offset, other, otherOffset, byteCount)

  actual open fun copyInto(
    offset: Int,
    target: ByteArray,
    targetOffset: Int,
    byteCount: Int,
  ) = commonCopyInto(offset, target, targetOffset, byteCount)

  actual fun startsWith(prefix: ByteString) = commonStartsWith(prefix)

  actual fun startsWith(prefix: ByteArray) = commonStartsWith(prefix)

  actual fun endsWith(suffix: ByteString) = commonEndsWith(suffix)

  actual fun endsWith(suffix: ByteArray) = commonEndsWith(suffix)

  actual fun indexOf(other: ByteString, fromIndex: Int) = indexOf(other.internalArray(), fromIndex)

  actual open fun indexOf(other: ByteArray, fromIndex: Int) = commonIndexOf(other, fromIndex)

  actual fun lastIndexOf(other: ByteString, fromIndex: Int) = commonLastIndexOf(other, fromIndex)

  actual open fun lastIndexOf(other: ByteArray, fromIndex: Int) = commonLastIndexOf(other, fromIndex)

  actual override fun equals(other: Any?) = commonEquals(other)

  actual override fun hashCode() = commonHashCode()

  actual override fun compareTo(other: ByteString) = commonCompareTo(other)

  /**
   * Returns a human-readable string that describes the contents of this byte string. Typically this
   * is a string like `[text=Hello]` or `[hex=0000ffff]`.
   */
  actual override fun toString() = commonToString()

  actual companion object {
    actual val EMPTY: ByteString = ByteString(byteArrayOf())

    actual fun of(vararg data: Byte) = commonOf(data)

    actual fun ByteArray.toByteString(offset: Int, byteCount: Int): ByteString =
      commonToByteString(offset, byteCount)

    actual fun String.encodeUtf8(): ByteString = commonEncodeUtf8()

    actual fun String.decodeBase64(): ByteString? = commonDecodeBase64()

    actual fun String.decodeHex() = commonDecodeHex()
  }
}
