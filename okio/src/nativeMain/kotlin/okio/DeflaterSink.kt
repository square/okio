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

import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_SYNC_FLUSH

class DeflaterSink(
  delegate: Sink,
) : Sink {
  internal val deflater = Deflater()
  private val target: BufferedSink = delegate.buffer()

  @Throws(IOException::class)
  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)

    deflater.flush = Z_NO_FLUSH
    deflater.writeBytesFromSource(
      source = source,
      sourceExactByteCount = byteCount,
      target = target,
    )
  }

  @Throws(IOException::class)
  override fun flush() {
    deflater.flush = Z_SYNC_FLUSH
    deflater.writeBytesFromSource(
      source = null,
      sourceExactByteCount = 0L,
      target = target,
    )

    target.flush()
  }

  override fun timeout(): Timeout {
    return target.timeout()
  }

  @Throws(IOException::class)
  override fun close() {
    if (deflater.closed) return

    // We must close the deflater and the target, even if flushing fails. Otherwise, we'll leak
    // resources! (And we re-throw whichever exception we catch first.)
    var thrown: Throwable? = null

    try {
      deflater.flush = Z_FINISH
      deflater.writeBytesFromSource(
        source = null,
        sourceExactByteCount = 0L,
        target = target,
      )
    } catch (e: Throwable) {
      thrown = e
    }

    deflater.close()

    try {
      target.close()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    if (thrown != null) throw thrown
  }
}
