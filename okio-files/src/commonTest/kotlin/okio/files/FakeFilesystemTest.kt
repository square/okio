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

import okio.FakeFilesystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeFilesystemTest : FileSystemTest(
  filesystem = FakeFilesystem(),
  temporaryDirectory = "/".toPath()
) {
  private val fakeFilesystem: FakeFilesystem = filesystem as FakeFilesystem

  @Test
  fun `open paths includes open sink`() {
    val openPath = base / "open-file"
    val sink = filesystem.sink(openPath)
    assertEquals(openPath, fakeFilesystem.openPaths.single())
    sink.close()
    assertTrue(fakeFilesystem.openPaths.isEmpty())
  }

  @Test
  fun `open paths includes open source`() {
    val openPath = base / "open-file"
    openPath.writeUtf8("hello, world!")
    assertTrue(fakeFilesystem.openPaths.isEmpty())
    val source = filesystem.source(openPath)
    assertEquals(openPath, fakeFilesystem.openPaths.single())
    source.close()
    assertTrue(fakeFilesystem.openPaths.isEmpty())
  }
}
