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
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.zlib.Z_BEST_COMPRESSION
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

internal val emptyByteArray = byteArrayOf()

/**
 * Deflate using Kotlin/Native's built-in zlib bindings. This uses the raw deflate format and omits
 * the zlib header and trailer, and does not compute a check value.
 *
 * To use:
 *
 *  1. Create an instance.
 *
 *  2. Populate [source] with uncompressed data. Set [sourcePos] and [sourceLimit] to a readable
 *     slice of this array.
 *
 *  3. Populate [target] with a destination for compressed data. Set [targetPos] and [targetLimit] to
 *     a writable slice of this array.
 *
 *  4. Call [deflate] to read input data from [source] and write compressed output to [target]. This
 *     function advances [sourcePos] if input data was read and [targetPos] if compressed output was
 *     written. If the input array is exhausted (`sourcePos == sourceLimit`) or the output array is
 *     full (`targetPos == targetLimit`), make an adjustment and call [deflate] again.
 *
 *  5. Repeat steps 2 through 4 until the input data is completely exhausted. Set [sourceFinished]
 *     to true before the last call to [deflate]. (It is okay to call deflate() when the source is
 *     exhausted.)
 *
 *  6. Close the Deflater.
 *
 * See also, the [zlib manual](https://www.zlib.net/manual.html).
 */
internal class Deflater : Closeable {
  private val zStream: z_stream_s = nativeHeap.alloc<z_stream_s> {
    zalloc = null
    zfree = null
    opaque = null
    check(
      deflateInit2(
        strm = ptr,
        level = Z_BEST_COMPRESSION,
        method = Z_DEFLATED,
        windowBits = -15, // Default value for raw deflate.
        memLevel = 8, // Default value.
        strategy = Z_DEFAULT_STRATEGY,
      ) == Z_OK,
    )
  }

  var source: ByteArray = emptyByteArray
  var sourcePos: Int = 0
  var sourceLimit: Int = 0
  var sourceFinished = false

  var target: ByteArray = emptyByteArray
  var targetPos: Int = 0
  var targetLimit: Int = 0

  private var closed = false

  /**
   * Returns true if no further calls to [deflate] are required to complete the operation.
   * Otherwise, make space available in [target] and call [deflate] again with the same arguments.
   */
  fun deflate(flush: Boolean = false): Boolean {
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

        val deflateFlush = when {
          sourceFinished -> Z_FINISH
          flush -> Z_SYNC_FLUSH
          else -> Z_NO_FLUSH
        }

        // One of Z_OK, Z_STREAM_END, Z_STREAM_ERROR, or Z_BUF_ERROR.
        val deflateResult = deflate(zStream.ptr, deflateFlush)
        check(deflateResult != Z_STREAM_ERROR)

        sourcePos += sourceByteCount - zStream.avail_in.toInt()
        targetPos += targetByteCount - zStream.avail_out.toInt()

        return when {
          sourceFinished -> deflateResult == Z_STREAM_END
          flush -> targetPos < targetLimit
          else -> true
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
