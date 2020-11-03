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
import okio.Filesystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** This test assumes that okio-files/ is the current working directory when executed. */
class FileSystemTest {
  @Test
  fun `cwd works`() {
    val cwd = Filesystem.SYSTEM.cwd()
    assertTrue(cwd.toString()) { cwd.toString().endsWith("okio/okio-files") }
  }

  @Test
  fun `list works`() {
    val entries = Filesystem.SYSTEM.list(Filesystem.SYSTEM.cwd())
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

  private fun Path.readUtf8(): String {
    val source = Filesystem.SYSTEM.source(this).buffer()
    try {
      return source.readUtf8()
    } finally {
      source.close()
    }
  }
}
