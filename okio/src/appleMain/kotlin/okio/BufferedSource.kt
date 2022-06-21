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
import platform.Foundation.*
import platform.darwin.NSInteger
import platform.darwin.NSUInteger
import platform.darwin.NSUIntegerVar
import platform.posix.memcpy
import platform.posix.uint8_tVar

@OptIn(UnsafeNumber::class)
fun BufferedSource.inputStream(): NSInputStream {
  return object : NSInputStream(NSData()) {

    private var error: NSError? = null

    private fun Exception.toNSError(): NSError {
      return NSError(
        "Kotlin",
        0,
        mapOf(
          NSLocalizedDescriptionKey to message,
          NSUnderlyingErrorKey to this
        )
      )
    }

    override fun streamError(): NSError? = error

    override fun read(buffer: CPointer<uint8_tVar>?, maxLength: NSUInteger): NSInteger {
      val bytes = ByteArray(maxLength.toInt())
      val read = try {
        this@inputStream.read(bytes, 0, maxLength.toInt())
      } catch (e: Exception) {
        error = e.toNSError()
        return -1
      }
      if (read > 0) {
        bytes.usePinned {
          memcpy(buffer, it.addressOf(0), read.toULong())
        }
        return read.toLong()
      }
      return 0
    }

    override fun getBuffer(
      buffer: CPointer<CPointerVar<uint8_tVar>>?,
      length: CPointer<NSUIntegerVar>?
    ): Boolean {
      if (this@inputStream.buffer.size > 0) {
        this@inputStream.buffer.head?.let { s ->
          s.data.usePinned {
            buffer?.pointed?.value = it.addressOf(s.pos).reinterpret()
            length?.pointed?.value = (s.limit - s.pos).toULong()
            return true
          }
        }
      }
      return false
    }

    override fun hasBytesAvailable(): Boolean = buffer.size > 0

    override fun close() = this@inputStream.close()

    override fun description(): String = "${this@inputStream}.inputStream()"
  }
}
