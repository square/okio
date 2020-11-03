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
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

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
    try {
      Filesystem.SYSTEM.list("/tmp/unlikely-directory/ce70dc67c24823e695e616145ce38403".toPath())
      fail()
    } catch (expected: IOException) {
    }
  }

  @Test
  fun `file source no such directory`() {
    try {
      Filesystem.SYSTEM.source("/tmp/unlikely-directory/ce70dc67c24823e695e616145ce38403".toPath())
      fail()
    } catch (expected: IOException) {
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
}
