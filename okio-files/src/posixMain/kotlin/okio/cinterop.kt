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

import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import platform.posix.strerror

internal fun Buffer.writeNullTerminated(bytes: CPointer<ByteVarOf<Byte>>): Buffer = apply {
  var pos = 0
  while (true) {
    val byte = bytes[pos++].toInt()
    if (byte == 0) {
      break
    } else {
      writeByte(byte)
    }
  }
}

internal fun Buffer.write(
  source: CPointer<ByteVarOf<Byte>>,
  offset: Int = 0,
  byteCount: Int
): Buffer = apply {
  for (i in offset until offset + byteCount) {
    writeByte(source[i].toInt())
  }
}

internal fun Buffer.read(
  sink: CPointer<ByteVarOf<Byte>>,
  offset: Int = 0,
  byteCount: Int
): Buffer = apply {
  for (i in offset until offset + byteCount) {
    sink[i] = readByte()
  }
}

internal fun errnoString(errno: Int): String {
  val message = strerror(errno)
  return if (message != null) {
    Buffer().writeNullTerminated(message).readUtf8()
  } else {
    "errno: $errno"
  }
}
