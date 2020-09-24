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

private class CipherSource(
  private val source: BufferedSource,
  private val cipher: Cipher
) : Source {
  constructor(source: Source, cipher: Cipher) : this(source.buffer(), cipher)

  private val blockSize = cipher.blockSize
  private val buffer = Buffer()
  private var final = false
  private var closed = false

  init {
    // Require block cipher, and check for unsupported (too large) block size (should never happen with standard algorithms)
    require(blockSize > 0) { "Block cipher required $cipher" }
    require(blockSize <= Segment.SIZE) { "Cipher block size $blockSize too large $cipher" }
  }

  @Throws(IOException::class)
  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    if (byteCount == 0L) return 0
    if (final) return buffer.read(sink, byteCount)

    refill()

    return buffer.read(sink, byteCount)
  }

  private fun refill() {
    while (buffer.size == 0L) {
      if (source.exhausted()) {
        final = true
        doFinal()
        break
      } else {
        update()
      }
    }
  }

  private fun update() {
    val head = source.buffer.head!!
    val size = head.limit - head.pos

    // For block cipher, output size cannot exceed input size in update
    val s = buffer.writableSegment(size)

    val ciphered =
      cipher.update(head.data, head.pos, size, s.data, s.pos)

    source.skip(size.toLong())

    s.limit += ciphered
    buffer.size += ciphered

    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }
  }

  private fun doFinal() {
    val outputSize = cipher.getOutputSize(0)
    if (outputSize == 0) return

    // For block cipher, output size cannot exceed block size in doFinal
    val s = buffer.writableSegment(outputSize)

    val ciphered = cipher.doFinal(s.data, s.pos)

    s.limit += ciphered
    buffer.size += ciphered

    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }
  }

  override fun timeout(): Timeout =
    source.timeout()

  @Throws(IOException::class)
  override fun close() {
    closed = true
    source.close()
  }
}

/**
 * Returns a [Source] that processes data using this [Cipher] while reading
 * from [source].
 *
 * @throws IllegalArgumentException
 *  If this isn't a block cipher.
 */
fun Cipher.source(source: Source): Source =
  CipherSource(source, this)
