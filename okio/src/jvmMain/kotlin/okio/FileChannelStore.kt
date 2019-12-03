/*
 * Copyright (C) 2019 Square, Inc.
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
import java.nio.channels.FileChannel

internal class FileChannelStore(
  private val channel: FileChannel,
  override val timeout: Timeout
) : Store {

  @Throws(IOException::class)
  override fun read(sink: Buffer, offset: Long, byteCount: Long): Long {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    if (byteCount == 0L) return 0
    if (offset == channel.size()) return -1

    var position = offset
    var remaining = byteCount
    while (remaining > 0) {
      timeout.throwIfReached()
      val read = channel.transferTo(position, remaining, sink)
      if (read == 0L) return byteCount - remaining
      remaining -= read
      position += read
    }
    return byteCount
  }

  @Throws(IOException::class)
  override fun write(source: Buffer, offset: Long, byteCount: Long) {
    require(byteCount >= 0) { "byteCount < 0: $byteCount" }
    if (byteCount == 0L) return

    var position = offset
    var remaining = byteCount
    while (byteCount > 0) {
      timeout.throwIfReached()
      val written = channel.transferFrom(source, position, remaining)
      remaining -= written
      position += written
    }
  }

  @Throws(IOException::class)
  override fun size(): Long = channel.size()

  @Throws(IOException::class)
  override fun truncate(size: Long) {
    channel.truncate(size)
  }

  @Throws(IOException::class)
  override fun flush() = channel.force(false) // Stores cannot update metadata

  @Throws(IOException::class)
  override fun close() = channel.close()
}
