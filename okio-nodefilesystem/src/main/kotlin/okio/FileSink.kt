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

internal class FileSink(
  private val fd: Number,
) : Sink {
  private var closed = false

  override fun write(source: Buffer, byteCount: Long) {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    require(source.size >= byteCount) { "source.size=${source.size} < byteCount=$byteCount" }
    check(!closed) { "closed" }

    val data = source.readByteArray(byteCount)
    val writtenByteCount = writeSync(fd, data)
    if (writtenByteCount.toLong() != byteCount) {
      throw IOException("expected $byteCount but was $writtenByteCount")
    }
  }

  override fun flush() {
  }

  override fun timeout(): Timeout {
    return Timeout.NONE
  }

  override fun close() {
    if (closed) return
    closed = true
    closeSync(fd)
  }
}
