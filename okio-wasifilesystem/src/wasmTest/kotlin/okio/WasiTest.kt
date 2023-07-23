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
package okio

import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath
import okio.ByteString.Companion.toByteString

class WasiTest {
  private val fileSystem = WasiFileSystem
  private val base: Path = "/tmp".toPath() / "${this::class.simpleName}-${randomToken(16)}"

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test
  fun writeAndReadLongFile() {
    println("before write")
    val fileName = base / "1m_numbers.txt"
    fileSystem.write(fileName) {
      for (i in 0L until 1_000_000L) {
//        writeDecimalLong(1234)
//        writeByte('\n'.code)
//      write(byteArrayOf('1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte(), 'x'.code.toByte()).toByteString())
      write(byteArrayOf('1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte(), '4'.code.toByte(), 'x'.code.toByte()).toByteString())
//        write(byteArrayOf(0, 0, 0, 0, 0).toByteString())
      }
    }
    println("before read")
    fileSystem.read(fileName) {
      println("before loop")
      for (i in 0L until 1_000_000L) {
        // if (i % 1000L == 0L) println(".. $i")
        readByteString(5)
      }
    }
    println("after read")
  }
}
