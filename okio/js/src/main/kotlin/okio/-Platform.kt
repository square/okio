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

package okio

import okio.internal.commonAsUtf8ToByteArray
import okio.internal.commonToUtf8String

internal actual fun arraycopy(
  src: ByteArray,
  srcPos: Int,
  dest: ByteArray,
  destPos: Int,
  length: Int
) {
  for (i in 0 until length) {
    dest[destPos + i] = src[srcPos + i]
  }
}

internal actual fun ByteArray.toUtf8String(): String = commonToUtf8String()

internal actual fun String.asUtf8ToByteArray(): ByteArray = commonAsUtf8ToByteArray()

actual open class ArrayIndexOutOfBoundsException actual constructor(
  message: String?
) : IndexOutOfBoundsException(message)

internal actual inline fun synchronizedOn(lock: Any, block: () -> Unit) = block()
