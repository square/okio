/*
 * Copyright (C) 2018 Square, Inc.
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

package okio.internal

internal abstract class AbstractMessageDigest : OkioMessageDigest {

  private var messageLength: Long = 0
  private var unprocessed = Bytes.EMPTY
  protected abstract var currentDigest: HashDigest

  override fun update(
    input: ByteArray,
    offset: Int,
    byteCount: Int
  ) {
    val bytes = unprocessed + input.toBytes().slice(offset until offset + byteCount)
    for (chunk in bytes.chunked(64)) {
      when (chunk.size) {
        64 -> {
          currentDigest = processChunk(chunk, currentDigest)
          messageLength += 64
        }
        else -> unprocessed = chunk
      }
    }
  }

  override fun digest(): ByteArray {
    val finalMessageLength = messageLength + unprocessed.size

    val finalMessage = byteArrayOf(
      *unprocessed.toByteArray(),
      0x80.toByte(),
      *ByteArray((56 - (finalMessageLength + 1) absMod 64).toInt()),
      *(finalMessageLength * 8L).toBigEndianByteArray()
    ).toBytes()

    finalMessage.chunked(64).forEach { chunk ->
      currentDigest = processChunk(chunk, currentDigest)
    }

    return currentDigest.toBigEndianByteArray()
  }

  protected abstract fun processChunk(chunk: Bytes, currentDigest: HashDigest): HashDigest
}
