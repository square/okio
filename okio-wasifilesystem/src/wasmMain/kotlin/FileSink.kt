/*
 * Copyright (C) 2023 Square, Inc.
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
import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Timeout
import okio.internal.preview1.fd
import okio.internal.preview1.fdClose
import okio.internal.preview1.fdWrite

class FileSink(
  private val fd: fd,
) : Sink {
  private var closed = false
  private val cursor = Buffer.UnsafeCursor()

  override fun write(source: Buffer, byteCount: Long) {
    require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
    require(source.size >= byteCount) { "source.size=${source.size} < byteCount=$byteCount" }
    check(!closed) { "closed" }

    var bytesRemaining = byteCount
    source.readAndWriteUnsafe(cursor)
    try {
      while (bytesRemaining > 0L) {
        check(cursor.next() != -1)

        val count = minOf(bytesRemaining, cursor.end.toLong() - cursor.start).toInt()
        if (fdWrite(fd, cursor.data!!, cursor.start, count) != count) {
          throw IOException("write failed")
        }
        bytesRemaining -= count
      }
    } finally {
      cursor.close()
      source.skip(byteCount)
    }
  }

  override fun flush() {
    // TODO
  }

  override fun timeout() = Timeout.NONE

  override fun close() {
    if (closed) return
    closed = true
    fdClose(fd)
  }
}
