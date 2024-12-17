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
package okio.samples

import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.Sink
import okio.Timeout
import okio.buffer
import okio.sink

/**
 * A sink that writes all input to both [sinkA] and [sinkB].
 */
class TeeSink(
  private val sinkA: Sink,
  private val sinkB: Sink,
) : Sink {
  private val timeout = Timeout()

  override fun write(source: Buffer, byteCount: Long) {
    // Writing to sink mutates source. Work around that.
    sinkA.timeout().intersectWith(timeout) {
      val buffer = Buffer()
      source.copyTo(buffer, byteCount = byteCount)
      sinkA.write(buffer, byteCount)
    }

    sinkB.timeout().intersectWith(timeout) {
      sinkB.write(source, byteCount)
    }
  }

  override fun flush() {
    sinkA.flush()
    sinkB.flush()
  }

  override fun close() {
    try {
      sinkA.close()
    } catch (tA: Throwable) {
      try {
        sinkB.close()
      } catch (tB: Throwable) {
        tA.addSuppressed(tB)
      }
      throw tA
    }

    sinkB.close()
  }

  override fun timeout() = sinkA.timeout()
}

fun main() {
  val a = System.out.sink()
  val b = FileSystem.SYSTEM.sink("tee.txt".toPath())

  TeeSink(a, b).buffer().use { teeSink ->
    teeSink.writeUtf8("hello\n")
    teeSink.flush()
    teeSink.writeUtf8("world!")
  }
}
