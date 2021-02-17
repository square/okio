/*
 * Copyright (C) 2021 Square, Inc.
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

@ExperimentalFileSystem
class SourceCursorTest {
  private val fileSystem = FileSystem.SYSTEM
  private var base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / randomToken()
  private val isJs = fileSystem::class.simpleName?.startsWith("NodeJs") ?: false

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test fun fileSourceCursorHappyPath() {
    if (isJs) return // TODO: implement cursors on Js platform.

    val path = base / "file-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val source = fileSystem.source(path)
    val cursor = source.cursor()!!
    assertEquals(16L, cursor.size())
    val buffer = Buffer()

    assertEquals(0L, cursor.position())
    assertEquals(4L, source.read(buffer, 4L))
    assertEquals("abcd", buffer.readUtf8())
    assertEquals(4L, cursor.position())

    cursor.seek(8L)
    assertEquals(8L, cursor.position())
    assertEquals(4L, source.read(buffer, 4L))
    assertEquals("ijkl", buffer.readUtf8())
    assertEquals(12L, cursor.position())

    cursor.seek(16L)
    assertEquals(16L, cursor.position())
    assertEquals(-1L, source.read(buffer, 4L))
    assertEquals("", buffer.readUtf8())
    assertEquals(16L, cursor.position())
  }

  @Test fun fileSourceCursorSeekBackwards() {
    if (isJs) return // TODO: implement cursors on Js platform.

    val path = base / "file-source-backwards"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val source = fileSystem.source(path)
    val cursor = source.cursor()!!
    assertEquals(16L, cursor.size())
    val buffer = Buffer()

    assertEquals(0L, cursor.position())
    assertEquals(16L, source.read(buffer, 16L))
    assertEquals("abcdefghijklmnop", buffer.readUtf8())
    assertEquals(16L, cursor.position())

    cursor.seek(0L)
    assertEquals(0L, cursor.position())
    assertEquals(16L, source.read(buffer, 16L))
    assertEquals("abcdefghijklmnop", buffer.readUtf8())
    assertEquals(16L, cursor.position())
  }

  @Test fun bufferedFileSourceCursorHappyPath() {
    if (isJs) return // TODO: implement cursors on Js platform.

    val path = base / "buffered-file-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val fileSource = fileSystem.source(path)
    val source = fileSource.buffer()
    val cursor = source.cursor()!!
    assertEquals(16L, cursor.size())

    assertEquals(0L, cursor.position())
    assertEquals("abcd", source.readUtf8(4L))
    assertEquals(4L, cursor.position())

    cursor.seek(8L)
    assertEquals(8L, source.buffer.size)
    assertEquals(8L, cursor.position())
    assertEquals("ijkl", source.readUtf8(4L))
    assertEquals(12L, cursor.position())

    cursor.seek(16L)
    assertEquals(0L, source.buffer.size)
    assertEquals(16L, cursor.position())
    assertEquals("", source.readUtf8())
    assertEquals(16L, cursor.position())
  }

  @Test fun bufferedFileSourceCursorSeekBackwards() {
    if (isJs) return // TODO: implement cursors on Js platform.

    val path = base / "buffered-file-source-backwards"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val fileSource = fileSystem.source(path)
    val source = fileSource.buffer()
    val cursor = source.cursor()!!
    assertEquals(16L, cursor.size())

    assertEquals(0L, cursor.position())
    assertEquals("abcdefghijklmnop", source.readUtf8(16L))
    assertEquals(16L, cursor.position())

    cursor.seek(0L)
    assertEquals(0L, source.buffer.size)
    assertEquals(0L, cursor.position())
    assertEquals("abcdefghijklmnop", source.readUtf8(16L))
    assertEquals(16L, cursor.position())
  }

  @Test fun bufferedFileSourceSeekBeyondBuffer() {
    if (isJs) return // TODO: implement cursors on Js platform.

    val path = base / "buffered-file-source-backwards"
    fileSystem.write(path) {
      writeUtf8("a".repeat(8192 * 2))
    }
    val fileSource = fileSystem.source(path)
    val source = fileSource.buffer()
    val cursor = source.cursor()!!
    assertEquals(8192 * 2, cursor.size())

    assertEquals(0L, cursor.position())
    assertEquals("aaaa", source.readUtf8(4L))
    assertEquals(4L, cursor.position())

    cursor.seek(8193L)
    assertEquals(0L, source.buffer.size)
    assertEquals(8193L, cursor.position())
    assertEquals("aaaa", source.readUtf8(4L))
    assertEquals(8197L, cursor.position())
  }
}
