/*
 * Copyright (C) 2019 Square, Inc.
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

import kotlinx.cinterop.Pinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.zlib.Z_DATA_ERROR
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_FINISH
import platform.zlib.Z_MEM_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_STREAM_END
import platform.zlib.Z_SYNC_FLUSH
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit
import platform.zlib.z_stream

class Deflater(level: Int = Z_DEFAULT_COMPRESSION) {
  private val stream: z_stream = nativeHeap.alloc<z_stream> {
    zalloc = null
    zfree = null
    opaque = null
  }.also {
    deflateInit(it.ptr, level)
  }

  val remaining: Int get() = stream.avail_in.toInt()

  private var pinned: Pinned<UByteArray>? = null
  private var finished: Boolean = false
  private var finish: Boolean = false

  fun setInput(input: ByteArray, off: Int, len: Int) {
    stream.avail_in = len.toUInt()

    pinned?.unpin()
    pinned = input.asUByteArray().pin().also {
      stream.next_in = it.addressOf(off)
    }
  }

  fun needsInput(): Boolean {
    return remaining == 0
  }

  fun finish() {
    finish = true
  }

  fun end() {
    pinned?.unpin()
    pinned = null
    deflateEnd(stream.ptr)
  }

  fun deflate(output: ByteArray, off: Int, len: Int, flush: Boolean = false): Int {
    val flushCode = when {
      finish -> Z_FINISH
      flush -> Z_SYNC_FLUSH
      else -> Z_NO_FLUSH
    }

    stream.avail_out = len.toUInt()
    output.asUByteArray().usePinned { data ->
      stream.next_out = data.addressOf(off)
      when (deflate(stream.ptr, flushCode)) {
        Z_STREAM_END -> finished = true
        Z_DATA_ERROR -> TODO()
        Z_MEM_ERROR -> TODO()
      }
    }
    return len - stream.avail_out.toInt()
  }

}
