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

internal class HashDigest(private vararg val hashValues: UInt) {

  fun toLittleEndianByteArray(): ByteArray {
    val size = hashValues.size * 4

    return ByteArray(size) { index ->
      val byteIndex = 3 - (index % 4)
      val hashValuesIndex = index / 4

      hashValues[hashValuesIndex].getByte(byteIndex)
    }
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
