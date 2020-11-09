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
package okio.files

import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.Filesystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** This test assumes that okio-files/ is the current working directory when executed. */
class FileSystemTest {
  val tmpDirectory = Filesystem.SYSTEM.tmpDirectory()

  @Test
  fun baseDirectory() {
    val cwd = Filesystem.SYSTEM.baseDirectory()
    assertTrue(cwd.toString()) { cwd.toString().endsWith("okio${Filesystem.SYSTEM.separator}okio-files") }
  }

  @Test
  fun list() {
    val entries = Filesystem.SYSTEM.list(Filesystem.SYSTEM.baseDirectory())
    assertTrue(entries.toString()) { "README.md" in entries.map { it.name } }
  }

  @Test
  fun `list no such directory`() {
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.list("$tmpDirectory/unlikely-directory/ce70dc67c24823e695e616145ce38403".toPath())
    }
  }

  @Test
  fun `file source no such directory`() {
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.source("$tmpDirectory/unlikely-directory/ce70dc67c24823e695e616145ce38403".toPath())
    }
  }

  @Test
  fun `file source`() {
    val source = Filesystem.SYSTEM.source("gradle.properties".toPath())
    val buffer = Buffer()
    assertTrue(source.read(buffer, 100L) <= 49L) // either 47 on posix or 49 with \r\n line feeds on windows
    assertEquals(-1L, source.read(buffer, 100L))
    assertTrue(buffer.readUtf8().contains("POM_ARTIFACT_ID=okio-files"))
    source.close()
  }

  @Test
  fun `file sink`() {
    val path = "$tmpDirectory/FileSystemTest-file_sink.txt".toPath()
    val sink = Filesystem.SYSTEM.sink(path)
    val buffer = Buffer().writeUtf8("hello, world!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
    assertEquals(0, buffer.size)
    assertEquals("hello, world!", path.readUtf8())
  }

  @Test
  fun `file sink flush`() {
    val path = "$tmpDirectory/FileSystemTest-file_sink.txt".toPath()
    val sink = Filesystem.SYSTEM.sink(path)

    val buffer = Buffer().writeUtf8("hello,")
    sink.write(buffer, buffer.size)
    sink.flush()
    assertEquals("hello,", path.readUtf8())

    buffer.writeUtf8(" world!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertEquals("hello, world!", path.readUtf8())
  }

  @Test
  fun `file sink no such directory`() {
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.sink("$tmpDirectory/ce70dc67c24823e695e616145ce38403/unlikely-file".toPath())
    }
  }

  @Test
  fun createDirectory() {
    val path = "$tmpDirectory/FileSystemTest-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(path)
    assertTrue(path in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
  }

  @Test
  fun `createDirectory parent directory does not exist`() {
    val path = "$tmpDirectory/ce70dc67c24823e695e616145ce38403-unlikely-file/created".toPath()
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.createDirectory(path)
    }
  }

  @Test
  fun `atomicMove file`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
    assertTrue(target in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
  }

  @Test
  fun `atomicMove directory`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(source)
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.atomicMove(source, target)
    assertTrue(source !in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
    assertTrue(target in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
  }

  @Test
  fun `atomicMove source is target`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    Filesystem.SYSTEM.atomicMove(source, source)
    assertEquals("hello, world!", source.readUtf8())
    assertTrue(source in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
  }

  @Test
  fun `atomicMove clobber existing file`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    target.writeUtf8("this file will be clobbered!")
    Filesystem.SYSTEM.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
    assertTrue(target in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
  }

  @Test
  fun `atomicMove source does not exist`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.atomicMove(source, target)
    }
  }

  @Test
  fun `atomicMove source is file and target is directory`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(target)
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.atomicMove(source, target)
    }
  }

  @Test
  fun `atomicMove source is directory and target is file`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(source)
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    target.writeUtf8("hello, world!")
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.atomicMove(source, target)
    }
  }

  @Test
  fun `copy file`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.copy(source, target)
    assertTrue(target in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
    assertEquals("hello, world!", target.readUtf8())
  }

  @Test
  fun `copy source does not exist`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.copy(source, target)
    }
    assertFalse(target in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
  }

  @Test
  fun `copy target is clobbered`() {
    val source = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    val target = "$tmpDirectory/FileSystemTest-atomicMove-${randomToken()}".toPath()
    target.writeUtf8("this file will be clobbered!")
    Filesystem.SYSTEM.copy(source, target)
    assertTrue(target in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
    assertEquals("hello, world!", target.readUtf8())
  }

  @Test
  fun `delete file`() {
    val path = "$tmpDirectory/FileSystemTest-delete-${randomToken()}".toPath()
    path.writeUtf8("delete me")
    Filesystem.SYSTEM.delete(path)
    assertTrue(path !in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
  }

  @Test
  fun `delete empty directory`() {
    val path = "$tmpDirectory/FileSystemTest-delete-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(path)
    Filesystem.SYSTEM.delete(path)
    assertTrue(path !in Filesystem.SYSTEM.list(tmpDirectory.toPath()))
  }

  @Test
  fun `delete fails on no such file`() {
    val path = "$tmpDirectory/FileSystemTest-delete-${randomToken()}".toPath()
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.delete(path)
    }
  }

  @Test
  fun `delete fails on nonempty directory`() {
    val path = "$tmpDirectory/FileSystemTest-delete-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(path)
    (path / "file.txt").writeUtf8("inside directory")
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.delete(path)
    }
  }

  private fun randomToken() = Random.nextBytes(16).toByteString().hex()

  private fun Path.readUtf8(): String {
    val source = Filesystem.SYSTEM.source(this).buffer()
    try {
      return source.readUtf8()
    } finally {
      source.close()
    }
  }

  private fun Path.writeUtf8(string: String) {
    val sink = Filesystem.SYSTEM.sink(this).buffer()
    try {
      sink.writeUtf8(string)
    } finally {
      sink.close()
    }
  }
}
