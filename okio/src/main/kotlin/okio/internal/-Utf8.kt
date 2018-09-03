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

import okio.processUtf8Bytes
import okio.processUtf16Chars

// TODO For benchmarking, these methods need to be available but preferably invisible
// to everything else. Putting them in this file, `-Utf8.kt`, makes them invisible to
// Java but still visible to Kotlin.

fun ByteArray.commonToUtf8String(): String {
  val chars = CharArray(size)

  var length = 0
  processUtf16Chars(0, size) { c ->
    chars[length++] = c
  }

  return String(chars, 0, length)
}

fun String.commonAsUtf8ToByteArray(): ByteArray {
  val bytes = ByteArray(4 * length)

  // Assume ASCII until a UTF-8 code point is observed. This is ugly but yields
  // about a 2x performance increase for pure ASCII.
  for (index in 0 until length) {
    val b0 = this[index]
    if (b0 >= '\u0080') {
      var size = index
      processUtf8Bytes(index, length) { c ->
        bytes[size++] = c
      }
      return bytes.copyOf(size)
    }
    bytes[index] = b0.toByte()
  }

  return bytes.copyOf(length)
}
