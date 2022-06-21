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

import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.NSInputStream
import platform.darwin.NSInteger
import platform.darwin.NSUInteger
import platform.darwin.NSUIntegerVar
import platform.posix.memcpy
import platform.posix.uint8_tVar

@OptIn(UnsafeNumber::class)
fun BufferedSource.inputStream(): NSInputStream {
  return object : NSInputStream(NSData()) {
    override fun read(buffer: CPointer<uint8_tVar>?, maxLength: NSUInteger): NSInteger {
      val bytes = ByteArray(maxLength.toInt())
      val read = this@inputStream.read(bytes, 0, maxLength.toInt())
      return if (read > 0) {
        bytes.usePinned {
          memcpy(buffer, it.addressOf(0), read.toULong())
        }
        read.toLong()
      } else {
        0
      }
    }

    override fun getBuffer(
      buffer: CPointer<CPointerVar<uint8_tVar>>?,
      length: CPointer<NSUIntegerVar>?
    ): Boolean {
      return false
    }

    override fun hasBytesAvailable(): Boolean {
      return buffer.size > 0
    }

    override fun close() = this@inputStream.close()

    override fun description(): String = "${this@inputStream}.inputStream()"
  }
}
