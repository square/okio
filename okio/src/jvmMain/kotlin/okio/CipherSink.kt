/*
 * Copyright (C) 2014 Square, Inc.
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

@file:JvmMultifileClass
@file:JvmName("Okio")

package okio

import java.io.IOException
import javax.crypto.Cipher

private class CipherSink internal constructor(
  private val sink: BufferedSink,
  private val cipher: Cipher
) : Sink {
  constructor(sink: Sink, cipher: Cipher) : this(sink.buffer(), cipher)

  private val blockSize = cipher.blockSize
  private var closed = false

  init {
    // Require block cipher, and check for unsupported (too large) block size (should never happen with standard algorithms)
    require(blockSize > 0) { "Block cipher required $cipher" }
    require(blockSize <= Segment.SIZE) { "Cipher block size $blockSize too large $cipher" }
  }

  @Throws(IOException::class)
  override fun write(source: Buffer, byteCount: Long) {
    checkOffsetAndCount(source.size, 0, byteCount)
    check(!closed) { "closed" }

    var remaining = byteCount
    while (remaining > 0) {
      val size = update(source, remaining)
      remaining -= size
    }
  }

  private fun update(source: Buffer, remaining: Long): Int {
    val head = source.head!!
    val size = minOf(remaining, head.limit - head.pos).toInt()
    val buffer = sink.buffer

    // For block cipher, output size cannot exceed input size in update
    val s = buffer.writableSegment(size)

    val ciphered = cipher.update(head.data, head.pos, size, s.data, s.limit)

    s.limit += ciphered
    buffer.size += ciphered

    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }

    source.size -= size
    head.pos += size

    if (head.pos == head.limit) {
      source.head = head.pop()
      SegmentPool.recycle(head)
    }
    return size
  }

  override fun flush() {}

  override fun timeout(): Timeout =
    sink.timeout()

  @Throws(IOException::class)
  override fun close() {
    if (closed) return

    var thrown = doFinal()

    try {
      sink.close()
    } catch (e: Throwable) {
      if (thrown == null) thrown = e
    }

    closed = true

    if (thrown != null) throw thrown
  }

  private fun doFinal(): Throwable? {
    val outputSize = cipher.getOutputSize(0)
    if (outputSize == 0) return null

    var thrown: Throwable? = null
    val buffer = sink.buffer

    // For block cipher, output size cannot exceed block size in doFinal
    val s = buffer.writableSegment(outputSize)

    try {
      val ciphered = cipher.doFinal(s.data, s.limit)

      s.limit += ciphered
      buffer.size += ciphered
    } catch (e: Throwable) {
      thrown = e
    }

    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }

    return thrown
  }
}

/**
 * Returns a [Sink] that processes data using this [Cipher] while writing to
 * [sink].
 */
fun Cipher.sink(sink: Sink): Sink =
  CipherSink(sink, this)
