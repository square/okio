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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test

@ExperimentalFileSystem
class SourceCursorTest {
  private val fileSystem = FileSystem.SYSTEM
  private var base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / randomToken()

  @Before
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test fun fileSourceCursorHappyPath() {
    val path = base / "file-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val source = fileSystem.source(path)
    val cursor = source.cursor()!!
    assertThat(cursor.size()).isEqualTo(16L)
    val buffer = Buffer()

    assertThat(cursor.position()).isEqualTo(0L)
    assertThat(source.read(buffer, 4L)).isEqualTo(4L)
    assertThat(buffer.readUtf8()).isEqualTo("abcd")
    assertThat(cursor.position()).isEqualTo(4L)

    cursor.seek(8L)
    assertThat(cursor.position()).isEqualTo(8L)
    assertThat(source.read(buffer, 4L)).isEqualTo(4L)
    assertThat(buffer.readUtf8()).isEqualTo("ijkl")
    assertThat(cursor.position()).isEqualTo(12L)

    cursor.seek(16L)
    assertThat(cursor.position()).isEqualTo(16L)
    assertThat(source.read(buffer, 4L)).isEqualTo(-1L)
    assertThat(buffer.readUtf8()).isEmpty()
    assertThat(cursor.position()).isEqualTo(16L)
  }

  @Test fun fileSourceCursorSeekBackwards() {
    val path = base / "file-source-backwards"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val source = fileSystem.source(path)
    val cursor = source.cursor()!!
    assertThat(cursor.size()).isEqualTo(16L)
    val buffer = Buffer()

    assertThat(cursor.position()).isEqualTo(0L)
    assertThat(source.read(buffer, 16L)).isEqualTo(16L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghijklmnop")
    assertThat(cursor.position()).isEqualTo(16L)

    cursor.seek(0L)
    assertThat(cursor.position()).isEqualTo(0L)
    assertThat(source.read(buffer, 16L)).isEqualTo(16L)
    assertThat(buffer.readUtf8()).isEqualTo("abcdefghijklmnop")
    assertThat(cursor.position()).isEqualTo(16L)
  }

  @Test fun bufferedFileSourceCursorHappyPath() {
    val path = base / "buffered-file-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val fileSource = fileSystem.source(path)
    val source = fileSource.buffer()
    val cursor = source.cursor()!!
    assertThat(cursor.size()).isEqualTo(16L)

    assertThat(cursor.position()).isEqualTo(0L)
    assertThat(source.readUtf8(4L)).isEqualTo("abcd")
    assertThat(cursor.position()).isEqualTo(4L)

    cursor.seek(8L)
    assertThat(source.buffer.size).isEqualTo(8L)
    assertThat(cursor.position()).isEqualTo(8L)
    assertThat(source.readUtf8(4L)).isEqualTo("ijkl")
    assertThat(cursor.position()).isEqualTo(12L)

    cursor.seek(16L)
    assertThat(source.buffer.size).isEqualTo(0L)
    assertThat(cursor.position()).isEqualTo(16L)
    assertThat(source.readUtf8()).isEmpty()
    assertThat(cursor.position()).isEqualTo(16L)
  }

  @Test fun bufferedFileSourceCursorSeekBackwards() {
    val path = base / "buffered-file-source-backwards"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val fileSource = fileSystem.source(path)
    val source = fileSource.buffer()
    val cursor = source.cursor()!!
    assertThat(cursor.size()).isEqualTo(16L)

    assertThat(cursor.position()).isEqualTo(0L)
    assertThat(source.readUtf8(16L)).isEqualTo("abcdefghijklmnop")
    assertThat(cursor.position()).isEqualTo(16L)

    cursor.seek(0L)
    assertThat(source.buffer.size).isEqualTo(0L)
    assertThat(cursor.position()).isEqualTo(0L)
    assertThat(source.readUtf8(16L)).isEqualTo("abcdefghijklmnop")
    assertThat(cursor.position()).isEqualTo(16L)
  }

  @Test fun bufferedFileSourceSeekBeyondBuffer() {
    val path = base / "buffered-file-source-backwards"
    fileSystem.write(path) {
      writeUtf8("a".repeat(8192 * 2))
    }
    val fileSource = fileSystem.source(path)
    val source = fileSource.buffer()
    val cursor = source.cursor()!!
    assertThat(cursor.size()).isEqualTo(8192 * 2)

    assertThat(cursor.position()).isEqualTo(0L)
    assertThat(source.readUtf8(4L)).isEqualTo("aaaa")
    assertThat(cursor.position()).isEqualTo(4L)

    cursor.seek(8193L)
    assertThat(source.buffer.size).isEqualTo(0L)
    assertThat(cursor.position()).isEqualTo(8193L)
    assertThat(source.readUtf8(4L)).isEqualTo("aaaa")
    assertThat(cursor.position()).isEqualTo(8197L)
  }
}
