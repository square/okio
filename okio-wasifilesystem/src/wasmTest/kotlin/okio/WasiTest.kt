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
import kotlin.test.assertFailsWith
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
  fun canonicalizeAbsolutePathNoSymlinks() {
    val path = base / "regular_file.txt"
    fileSystem.write(path) {
      writeUtf8("hello")
    }
    assertEquals(
      path,
      fileSystem.canonicalize(path),
    )
  }

  @Test
  fun canonicalizeAbsolutePathWithSymlinksInFiles() {
    val target = base / "target"
    val source = base / "source"
    fileSystem.write(target) {
      writeUtf8("hello")
    }
    fileSystem.createSymlink(source, "target".toPath())
    assertEquals(
      target,
      fileSystem.canonicalize(source),
    )
  }

  @Test
  fun canonicalizeAbsolutePathWithSymlinksInDirectories() {
    val target = base / "target"
    val source = base / "source"
    fileSystem.createDirectory(target)
    fileSystem.write(target / "file.txt") {
      writeUtf8("hello")
    }
    fileSystem.createSymlink(source, "target".toPath())
    assertEquals(
      target / "file.txt",
      fileSystem.canonicalize(source / "file.txt"),
    )
  }

  @Test
  fun canonicalizeAbsolutePathWithSymlinkCycle() {
    fileSystem.createSymlink(base / "rock", "scissors".toPath())
    fileSystem.createSymlink(base / "scissors", "paper".toPath())
    fileSystem.createSymlink(base / "paper", "rock".toPath())
    val e = assertFailsWith<IOException> {
      fileSystem.canonicalize(base / "rock")
    }
    assertEquals("symlink cycle?", e.message)
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
  fun appendToFile() {
    val fileName = base / "append.txt"
    fileSystem.write(fileName) {
      writeUtf8("hello")
    }
    fileSystem.appendingSink(fileName).buffer().use {
      it.writeUtf8(" world")
    }
    assertEquals(
      "hello world",
      fileSystem.read(fileName) {
        readUtf8()
      },
    )
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
  fun deleteFile() {
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
  fun deleteDirectory() {
    fileSystem.createDirectory(base / "a")
    fileSystem.createDirectory(base / "b")
    fileSystem.createDirectory(base / "c")
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
      writeUtf8("this is the target file's contents")
    }
    fileSystem.createSymlink(sourcePath, "target".toPath())

    assertEquals(
      "this is the target file's contents",
      fileSystem.read(sourcePath) {
        readUtf8()
      },
    )
  }

  @Test
  fun rename() {
    val targetPath = base / "target"
    val sourcePath = base / "source"
    fileSystem.write(sourcePath) {
      writeUtf8("this is the file's contents")
    }
    fileSystem.atomicMove(sourcePath, targetPath)

    assertEquals(
      "this is the file's contents",
      fileSystem.read(targetPath) {
        readUtf8()
      },
    )
    assertEquals(
      listOf(targetPath),
      fileSystem.list(base),
    )
  }

  @Test
  fun fileMetadata() {
    val regularFile = base / "regularFile"
    val directory = base / "directory"
    val symlink = base / "symlink"
    fileSystem.write(regularFile) {
      writeUtf8("this is a regular file")
    }
    fileSystem.createDirectory(directory)
    fileSystem.createSymlink(symlink, "regularFile".toPath())

    val regularFileMetadata = fileSystem.metadata(regularFile)
    assertEquals(true, regularFileMetadata.isRegularFile)
    assertEquals(false, regularFileMetadata.isDirectory)
    assertEquals(null, regularFileMetadata.symlinkTarget)
    assertEquals(22L, regularFileMetadata.size)

    val directoryMetadata = fileSystem.metadata(directory)
    assertEquals(false, directoryMetadata.isRegularFile)
    assertEquals(true, directoryMetadata.isDirectory)
    assertEquals(null, directoryMetadata.symlinkTarget)
    // Note: no assertions about directory size.

    val symlinkMetadata = fileSystem.metadata(symlink)
    assertEquals(false, symlinkMetadata.isRegularFile)
    assertEquals(false, symlinkMetadata.isDirectory)
    assertEquals("regularFile".toPath(), symlinkMetadata.symlinkTarget)
    assertEquals("regularFile".length.toLong(), symlinkMetadata.size)
  }

  @Test
  fun absentMetadata() {
    assertEquals(null, fileSystem.metadataOrNull(base / "no-such-file"))
    assertFailsWith<FileNotFoundException> {
      fileSystem.metadata(base / "no-such-file")
    }
  }

  @Test
  fun fileHandleRead() {
    val path = base / "file.txt"
    fileSystem.write(path) {
      writeUtf8("this is a file about dogs and cats")
    }
    fileSystem.openReadOnly(path).use { handle ->
      val sink = Buffer()
      handle.read(21L, sink, 4L)

      assertEquals(
        "dogs",
        sink.readUtf8(),
      )
    }
  }

  @Test
  fun fileHandleWrite() {
    val path = base / "file.txt"
    fileSystem.write(path) {
      writeUtf8("this is a file about cats and cats")
    }
    fileSystem.openReadWrite(path).use { handle ->
      val source = Buffer().writeUtf8("dogs")
      handle.write(21L, source, 4L)

      assertEquals(
        "this is a file about dogs and cats",
        fileSystem.read(path) {
          readUtf8()
        },
      )
    }
  }

  @Test
  fun fileHandleGetSize() {
    val path = base / "file.txt"
    fileSystem.write(path) {
      writeUtf8("this is a file about dogs and cats")
    }
    fileSystem.openReadOnly(path).use { handle ->
      assertEquals(
        34L,
        handle.size(),
      )
    }
  }

  @Test
  fun fileHandleResize() {
    val path = base / "file.txt"
    fileSystem.write(path) {
      writeUtf8("this is a file about dogs and cats")
    }
    fileSystem.openReadWrite(path).use { handle ->
      handle.resize(25L)

      assertEquals(
        "this is a file about dogs",
        fileSystem.read(path) {
          readUtf8()
        },
      )
    }
  }

  @Test
  fun fileHandleFlush() {
    val path = base / "file.txt"
    fileSystem.openReadWrite(path).use { handle ->
      handle.sink().buffer().use {
        it.writeUtf8("hello")
      }
      handle.flush()

      assertEquals(
        "hello",
        fileSystem.read(path) {
          readUtf8()
        },
      )
    }
  }

  @Test
  fun fileSinkFlush() {
    val path = base / "file.txt"
    fileSystem.write(path) {
      writeUtf8("hello")
      flush()

      assertEquals(
        "hello",
        fileSystem.read(path) {
          readUtf8()
        },
      )
    }
  }
}
