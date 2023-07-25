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

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath

class WasiTest {
  private val fileSystem = WasiFileSystem
  private val base: Path = "/tmp".toPath() / "${this::class.simpleName}-${randomToken(16)}"

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test
  fun createDirectory() {
    fileSystem.createDirectory(base / "child")
  }

  @Test
  fun writeAndReadEmptyFile() {
    writeAndReadFile(ByteString.EMPTY, base / "empty.txt")
  }

  @Test
  fun writeAndReadShortFile() {
    writeAndReadFile("hello\n".encodeUtf8(), base / "hello.txt")
  }

  private fun writeAndReadFile(content: ByteString, fileName: Path) {
    fileSystem.write(fileName) {
      write(content)
    }
    assertEquals(
      content,
      fileSystem.read(fileName) {
        readByteString()
      },
    )
  }

  @Test
  fun writeAndReadLongFile() {
    val fileName = base / "5m_bytes.txt"
    fileSystem.write(fileName) {
      for (i in 0L until 1_000_000L) {
        writeByte(i.toInt())
        writeByte(0)
        writeByte(0)
        writeByte(0)
        writeByte(0)
      }
    }
    fileSystem.read(fileName) {
      for (i in 0L until 1_000_000L) {
        assertEquals(i.toByte(), readByte())
        assertEquals(0, readByte())
        assertEquals(0, readByte())
        assertEquals(0, readByte())
        assertEquals(0, readByte())
      }
      assertTrue(exhausted())
    }
  }

  @Test
  fun listDirectory() {
    fileSystem.write(base / "a") {
      writeUtf8("this file has a 1-byte file name")
    }
    fileSystem.write(base / "a.txt") {
      writeUtf8("this file has a 5-byte file name")
    }

    assertEquals(
      listOf(
        base / "a",
        base / "a.txt",
      ),
      fileSystem.list(base).sorted(),
    )
  }

  @Test
  fun delete() {
    fileSystem.write(base / "a") {
    }
    fileSystem.write(base / "b") {
    }
    fileSystem.write(base / "c") {
    }
    fileSystem.delete(base / "b")

    assertEquals(
      listOf(
        base / "a",
        base / "c",
      ),
      fileSystem.list(base).sorted(),
    )
  }

  @Test
  fun createSymlink() {
    val targetPath = base / "target"
    val sourcePath = base / "source"
    fileSystem.write(targetPath) {
      writeUtf8("this is the the target file's contents")
    }
    fileSystem.createSymlink(sourcePath, "target".toPath())

    assertEquals(
      "this is the the target file's contents",
      fileSystem.read(sourcePath) {
        readUtf8()
      },
    )
  }
}
