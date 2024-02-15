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
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_STREAM_ERROR
import platform.zlib.Z_SYNC_FLUSH
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.z_stream_s

/**
 * Deflate using Kotlin/Native's built-in zlib bindings. This uses the raw deflate format and omits
 * the zlib header and trailer, and does not compute a check value.
 *
 * Note that you must set [flush] to [Z_FINISH] before the last call to [process]. (It is okay to
 * call process() when the source is exhausted.)
 *
 * See also, the [zlib manual](https://www.zlib.net/manual.html).
 */
actual class Deflater actual constructor(
  level: Int,
  nowrap: Boolean,
) {
  private val zStream: z_stream_s = nativeHeap.alloc<z_stream_s> {
    zalloc = null
    zfree = null
    opaque = null
    check(
      deflateInit2(
        strm = ptr,
        level = level,
        method = Z_DEFLATED,
        windowBits = -15, // Default value for raw deflate.
        memLevel = 8, // Default value.
        strategy = Z_DEFAULT_STRATEGY,
      ) == Z_OK,
    )
  }

  /** Probably [Z_NO_FLUSH], [Z_FINISH], or [Z_SYNC_FLUSH]. */
  var flush: Int = Z_NO_FLUSH

  actual constructor() : this(Z_DEFAULT_COMPRESSION, false)

  init {
    require(nowrap) {
      "nowrap = $nowrap not implemented yet"
    }
  }

  internal val dataProcessor: DataProcessor = object : DataProcessor() {
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

          // One of Z_OK, Z_STREAM_END, Z_STREAM_ERROR, or Z_BUF_ERROR.
          val deflateResult = deflate(zStream.ptr, flush)
          check(deflateResult != Z_STREAM_ERROR)

          sourcePos += sourceByteCount - zStream.avail_in.toInt()
          targetPos += targetByteCount - zStream.avail_out.toInt()

          return when (deflateResult) {
            Z_STREAM_END -> true
            else -> targetPos < targetLimit
          }
        }
      }
    }

    override fun close() {
      if (closed) return
      closed = true

      deflateEnd(zStream.ptr)
      nativeHeap.free(zStream)
    }
  }

  @OptIn(UnsafeNumber::class)
  actual fun getBytesRead(): Long {
    return zStream.total_in.toLong() // TODO: test
  }

  actual fun end() {
    dataProcessor.close()
  }
}
