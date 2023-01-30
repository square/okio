/*
 * Copyright (C) 2020 Square, Inc. and others.
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

import java.io.IOException
import javax.crypto.Cipher

class CipherSource(
  private val source: BufferedSource,
  val cipher: Cipher,
) : Source {
  private val blockSize = cipher.blockSize
  private val buffer = Buffer()
  private var final = false
  private var closed = false

  init {
    // Require block cipher
    require(blockSize > 0) { "Block cipher required $cipher" }
  }

  @Throws(IOException::class)
  override fun read(sink: Buffer, byteCount: Long): Long {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    check(!closed) { "closed" }
    if (byteCount == 0L) return 0L

    refill()

    return buffer.read(sink, byteCount)
  }

  private fun refill() {
    while (buffer.size == 0L && !final) {
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
    var size = head.limit - head.pos

    // Shorten input until output is guaranteed to fit within a segment
    var outputSize = cipher.getOutputSize(size)
    while (outputSize > Segment.SIZE) {
      if (size <= blockSize) {
        // Bug: For AES-GCM on Android `update` method never outputs any data
        // As a consequence, `getOutputSize` just keeps increasing indefinitely after each update
        // When that happens, the fallback is to break the streaming implementation and just cipher the rest of the data all at once
        final = true
        buffer.write(cipher.doFinal(source.readByteArray()))
        return
      }
      size -= blockSize
      outputSize = cipher.getOutputSize(size)
    }
    val s = buffer.writableSegment(outputSize)

    val ciphered =
      cipher.update(head.data, head.pos, size, s.data, s.pos)

    source.skip(size.toLong())

    s.limit += ciphered
    buffer.size += ciphered

    // We allocated a tail segment, but didn't end up needing it. Recycle!
    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }
  }

  private fun doFinal() {
    val outputSize = cipher.getOutputSize(0)
    if (outputSize == 0) return

    // For block cipher, output size cannot exceed block size in doFinal.
    val s = buffer.writableSegment(outputSize)

    val ciphered = cipher.doFinal(s.data, s.pos)

    s.limit += ciphered
    buffer.size += ciphered

    // We allocated a tail segment, but didn't end up needing it. Recycle!
    if (s.pos == s.limit) {
      buffer.head = s.pop()
      SegmentPool.recycle(s)
    }
  }

  override fun timeout() = source.timeout()

  @Throws(IOException::class)
  override fun close() {
    closed = true
    source.close()
  }
}
