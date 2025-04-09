/*
 * Copyright (C) 2025 Square, Inc.
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

import okio.ByteString

private val charToNibble = js(
  """
    {
      0: 0, 1: 1, 2: 2, 3: 3, 4: 4, 5: 5, 6: 6, 7: 7, 8: 8, 9: 9,
      a: 10, b: 11, c: 12, d: 13, e: 14, f: 15,
      A: 10, B: 11, C: 12, D: 13, E: 14, F: 15
    }
    """,
)

/**
 * Here we implement a custom hex decoder because the vanilla Kotlin one is too slow. The Kotlin
 * transpiles to reasonable-looking but very inefficient JavaScript!
 *
 * This does a plain JavaScript implementation of hex decoding, and it's dramatically faster. In
 * one measurement hex decoding went from 25% of CPU samples to 0% of them.
 */
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun String.commonDecodeHex(): ByteString {
  require(length % 2 == 0) { "Unexpected hex string: $this" }

  val string = this
  val charToNibble = charToNibble
  val result = ByteArray(string.length / 2)
  var invalidDigitIndex = -1

  js(
    """
      var stringIndex = 0;
      var byteIndex = 0;
      while (stringIndex < string.length) {
        var charA = string[stringIndex++];
        var nibbleA = charToNibble[charA];

        var charB = string[stringIndex++];
        var nibbleB = charToNibble[charB];

        if (nibbleA == null || nibbleB == null) {
          invalidDigitIndex = stringIndex;
          break;
        }

        result[byteIndex++] = (nibbleA << 4) | nibbleB;
      }
      """,
  )

  require(invalidDigitIndex == -1) {
    "Unexpected hex digit: ${string[invalidDigitIndex]}"
  }

  return ByteString(result)
}
