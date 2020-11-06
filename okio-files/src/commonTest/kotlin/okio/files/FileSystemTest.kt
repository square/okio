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
import kotlin.test.assertTrue

/** This test assumes that okio-files/ is the current working directory when executed. */
class FileSystemTest {
  @Test
  fun baseDirectory() {
    val cwd = Filesystem.SYSTEM.baseDirectory()
    assertTrue(cwd.toString()) { cwd.toString().endsWith("okio/okio-files") }
  }

  @Test
  fun list() {
    val entries = Filesystem.SYSTEM.list(Filesystem.SYSTEM.baseDirectory())
    assertTrue(entries.toString()) { "README.md" in entries.map { it.name } }
  }

  @Test
  fun `list no such directory`() {
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.list("/tmp/unlikely-directory/ce70dc67c24823e695e616145ce38403".toPath())
    }
  }

  @Test
  fun `file source no such directory`() {
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.source("/tmp/unlikely-directory/ce70dc67c24823e695e616145ce38403".toPath())
    }
  }

  @Test
  fun `file source`() {
    val source = Filesystem.SYSTEM.source("gradle.properties".toPath())
    val buffer = Buffer()
    assertEquals(47L, source.read(buffer, 100L))
    assertEquals(-1L, source.read(buffer, 100L))
    assertEquals("""
        |POM_ARTIFACT_ID=okio-files
        |POM_NAME=Okio Files
        |""".trimMargin(), buffer.readUtf8())
    source.close()
  }

  @Test
  fun `file sink`() {
    val path = "/tmp/FileSystemTest-file_sink.txt".toPath()
    val sink = Filesystem.SYSTEM.sink(path)
    val buffer = Buffer().writeUtf8("hello, world!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in Filesystem.SYSTEM.list("/tmp".toPath()))
    assertEquals(0, buffer.size)
    assertEquals("hello, world!", path.readUtf8())
  }

  @Test
  fun `file sink flush`() {
    val path = "/tmp/FileSystemTest-file_sink.txt".toPath()
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
      Filesystem.SYSTEM.sink("/tmp/ce70dc67c24823e695e616145ce38403/unlikely-file".toPath())
    }
  }

  @Test
  fun createDirectory() {
    val path = "/tmp/FileSystemTest-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(path)
    assertTrue(path in Filesystem.SYSTEM.list("/tmp".toPath()))
  }

  @Test
  fun `createDirectory parent directory does not exist`() {
    val path = "/tmp/ce70dc67c24823e695e616145ce38403-unlikely-file/created".toPath()
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.createDirectory(path)
    }
  }

  @Test
  fun `atomicMove file`() {
    val source = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    val target = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in Filesystem.SYSTEM.list("/tmp".toPath()))
    assertTrue(target in Filesystem.SYSTEM.list("/tmp".toPath()))
  }

  @Test
  fun `atomicMove directory`() {
    val source = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(source)
    val target = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.atomicMove(source, target)
    assertTrue(source !in Filesystem.SYSTEM.list("/tmp".toPath()))
    assertTrue(target in Filesystem.SYSTEM.list("/tmp".toPath()))
  }

  @Test
  fun `atomicMove source is target`() {
    val source = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    Filesystem.SYSTEM.atomicMove(source, source)
    assertEquals("hello, world!", source.readUtf8())
    assertTrue(source in Filesystem.SYSTEM.list("/tmp".toPath()))
  }

  @Test
  fun `atomicMove clobber existing file`() {
    val source = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    val target = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    target.writeUtf8("this file will be clobbered!")
    Filesystem.SYSTEM.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in Filesystem.SYSTEM.list("/tmp".toPath()))
    assertTrue(target in Filesystem.SYSTEM.list("/tmp".toPath()))
  }

  @Test
  fun `atomicMove source does not exist`() {
    val source = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    val target = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.atomicMove(source, target)
    }
  }

  @Test
  fun `atomicMove source is file and target is directory`() {
    val source = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    source.writeUtf8("hello, world!")
    val target = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(target)
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.atomicMove(source, target)
    }
  }

  @Test
  fun `atomicMove source is directory and target is file`() {
    val source = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    Filesystem.SYSTEM.createDirectory(source)
    val target = "/tmp/FileSystemTest-atomicMove-${randomToken()}".toPath()
    target.writeUtf8("hello, world!")
    assertFailsWith<IOException> {
      Filesystem.SYSTEM.atomicMove(source, target)
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
