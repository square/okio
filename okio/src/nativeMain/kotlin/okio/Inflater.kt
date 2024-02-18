/*
 * Copyright (C) 2024 Square, Inc.
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
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_DATA_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream_s

/**
 * Inflate using Kotlin/Native's built-in zlib bindings.
 */
actual class Inflater actual constructor(
  nowrap: Boolean,
) {
  private val zStream: z_stream_s = nativeHeap.alloc<z_stream_s> {
    zalloc = null
    zfree = null
    opaque = null
    check(
      inflateInit2(
        strm = ptr,
        windowBits = if (nowrap) -15 else 15, // Negative for raw deflate.
      ) == Z_OK,
    )
  }

  internal val dataProcessor: DataProcessor = object : DataProcessor() {
    @Throws(ProtocolException::class)
    override fun process(): Boolean {
      check(!closed) { "closed" }
      require(0 <= sourcePos && sourcePos <= sourceLimit && sourceLimit <= source.size)
      require(0 <= targetPos && targetPos <= targetLimit && targetLimit <= target.size)

      source.usePinned { pinnedSource ->
        target.usePinned { pinnedTarget ->
          val sourceByteCount = sourceLimit - sourcePos
          zStream.next_in = when {
            sourceByteCount > 0 -> pinnedSource.addressOf(sourcePos) as CPointer<UByteVar>
            else -> null
          }
          zStream.avail_in = sourceByteCount.toUInt()

          val targetByteCount = targetLimit - targetPos
          zStream.next_out = when {
            targetByteCount > 0 -> pinnedTarget.addressOf(targetPos) as CPointer<UByteVar>
            else -> null
          }
          zStream.avail_out = targetByteCount.toUInt()

          val inflateResult = platform.zlib.inflate(zStream.ptr, Z_NO_FLUSH)

          sourcePos += sourceByteCount - zStream.avail_in.toInt()
          targetPos += targetByteCount - zStream.avail_out.toInt()

          when (inflateResult) {
            Z_OK, Z_BUF_ERROR -> {
              return targetPos < targetLimit
            }

            Z_STREAM_END -> {
              finished = true
              return true
            }

            Z_DATA_ERROR -> throw ProtocolException("Z_DATA_ERROR")

            // One of Z_NEED_DICT, Z_STREAM_ERROR, Z_MEM_ERROR.
            else -> throw ProtocolException("unexpected inflate result: $inflateResult")
          }
        }
      }
    }

    override fun close() {
      if (closed) return
      closed = true

      inflateEnd(zStream.ptr)
      nativeHeap.free(zStream)
    }
  }

  actual constructor() : this(false)

  @OptIn(UnsafeNumber::class)
  actual fun getBytesWritten(): Long {
    check(!dataProcessor.closed) { "closed" }
    return zStream.total_out.toLong()
  }

  actual fun end() {
    dataProcessor.close()
  }
}
