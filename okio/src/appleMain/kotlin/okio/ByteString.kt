/*
 * Copyright (C) 2020 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

@OptIn(UnsafeNumber::class)
fun NSData.toByteString(): ByteString {
  val data = this
  val size = data.length.toInt()
  return if (size != 0) {
    ByteString(
      ByteArray(size).apply {
        usePinned { pinned ->
          memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
      }
    )
  } else {
    ByteString.EMPTY
  }
}
