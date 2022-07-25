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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pin
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSInputStream
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSUnderlyingErrorKey
import platform.darwin.NSInteger
import platform.darwin.NSUInteger
import platform.darwin.NSUIntegerVar
import platform.posix.memcpy
import platform.posix.uint8_tVar

/** Returns an input stream that reads from this source. */
fun BufferedSource.inputStream(): NSInputStream = BufferedSourceInputStream(this)

@OptIn(UnsafeNumber::class)
private class BufferedSourceInputStream(
  private val bufferedSource: BufferedSource
) : NSInputStream(NSData()) {

  private var error: NSError? = null
  private var pinnedBuffer: Pinned<ByteArray>? = null

  override fun streamError(): NSError? = error

  override fun open() {
    // no-op
  }

  override fun read(buffer: CPointer<uint8_tVar>?, maxLength: NSUInteger): NSInteger {
    try {
      if (bufferedSource is RealBufferedSource) {
        if (bufferedSource.closed) throw IOException("closed")
        if (bufferedSource.exhausted()) return 0
      }

      val toRead = minOf(maxLength.toInt(), bufferedSource.buffer.size).toInt()
      return bufferedSource.buffer.readNative(buffer, toRead).convert()
    } catch (e: Exception) {
      error = e.toNSError()
      return -1
    }
  }

  override fun getBuffer(
    buffer: CPointer<CPointerVar<uint8_tVar>>?,
    length: CPointer<NSUIntegerVar>?
  ): Boolean {
    if (bufferedSource.buffer.size > 0) {
      bufferedSource.buffer.head?.let { s ->
        pinnedBuffer?.unpin()
        s.data.pin().let {
          pinnedBuffer = it
          buffer?.pointed?.value = it.addressOf(s.pos).reinterpret()
          length?.pointed?.value = (s.limit - s.pos).convert()
          return true
        }
      }
    }
    return false
  }

  override fun hasBytesAvailable(): Boolean = bufferedSource.buffer.size > 0

  override fun close() {
    pinnedBuffer?.unpin()
    bufferedSource.close()
  }

  override fun description(): String = "$bufferedSource.inputStream()"

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

  private fun Buffer.readNative(sink: CPointer<uint8_tVar>?, maxLength: Int): Int {
    val s = head ?: return 0
    val toCopy = minOf(maxLength, s.limit - s.pos)
    s.data.usePinned {
      memcpy(sink, it.addressOf(s.pos), toCopy.convert())
    }

    s.pos += toCopy
    size -= toCopy.toLong()

    if (s.pos == s.limit) {
      head = s.pop()
      SegmentPool.recycle(s)
    }

    return toCopy
  }
}
