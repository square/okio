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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.FakeFilesystem
import okio.Filesystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.createdAt
import okio.lastAccessedAt
import okio.lastModifiedAt
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

/** This test assumes that okio-files/ is the current working directory when executed. */
@ExperimentalTime
abstract class FileSystemTest(
  val clock: Clock,
  val filesystem: Filesystem,
  temporaryDirectory: Path
) {
  val base: Path = temporaryDirectory / "FileSystemTest-${randomToken()}"

  @BeforeTest
  fun setUp() {
    filesystem.createDirectory(base)
  }

  @Test
  fun `canonicalize dot returns current working directory`() {
    if (filesystem is FakeFilesystem) return
    val cwd = filesystem.canonicalize(".".toPath())
    assertTrue(cwd.toString()) {
      cwd.toString().endsWith("okio${Path.directorySeparator}okio-files")
    }
  }

  @Test
  fun `canonicalize no such file`() {
    assertFailsWith<IOException> {
      filesystem.canonicalize(base / "no-such-file")
    }
  }

  @Test
  fun list() {
    val target = base / "list"
    target.writeUtf8("hello, world!")
    val entries = filesystem.list(base)
    assertTrue(entries.toString()) { target in entries }
  }

  @Test
  fun `list no such directory`() {
    assertFailsWith<IOException> {
      filesystem.list(base / "no-such-directory")
    }
  }

  @Test
  fun `file source no such directory`() {
    assertFailsWith<IOException> {
      filesystem.source(base / "no-such-directory" / "file")
    }
  }

  @Test
  fun `file source`() {
    val path = base / "file-source"
    path.writeUtf8("hello, world!")

    val source = filesystem.source(path)
    val buffer = Buffer()
    assertTrue(source.read(buffer, 100L) == 13L)
    assertEquals(-1L, source.read(buffer, 100L))
    assertEquals("hello, world!", buffer.readUtf8())
    source.close()
  }

  @Test
  fun `file sink`() {
    val path = base / "file=sink"
    val sink = filesystem.sink(path)
    val buffer = Buffer().writeUtf8("hello, world!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in filesystem.list(base))
    assertEquals(0, buffer.size)
    assertEquals("hello, world!", path.readUtf8())
  }

  @Test
  fun `file sink flush`() {
    val path = base / "file-sink"
    val sink = filesystem.sink(path)

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
      filesystem.sink(base / "no-such-directory" / "file")
    }
  }

  @Test
  fun createDirectory() {
    val path = base / "create-directory"
    filesystem.createDirectory(path)
    assertTrue(path in filesystem.list(base))
  }

  @Test
  fun `createDirectory parent directory does not exist`() {
    val path = base / "no-such-directory" / "created"
    assertFailsWith<IOException> {
      filesystem.createDirectory(path)
    }
  }

  @Test
  fun `atomicMove file`() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    filesystem.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in filesystem.list(base))
    assertTrue(target in filesystem.list(base))
  }

  @Test
  fun `atomicMove directory`() {
    val source = base / "source"
    filesystem.createDirectory(source)
    val target = base / "target"
    filesystem.atomicMove(source, target)
    assertTrue(source !in filesystem.list(base))
    assertTrue(target in filesystem.list(base))
  }

  @Test
  fun `atomicMove source is target`() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    filesystem.atomicMove(source, source)
    assertEquals("hello, world!", source.readUtf8())
    assertTrue(source in filesystem.list(base))
  }

  @Test
  @Ignore // TODO(jwilson): Windows has different behavior for this test. Fix and re-enable.
  fun `atomicMove clobber existing file`() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    target.writeUtf8("this file will be clobbered!")
    filesystem.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in filesystem.list(base))
    assertTrue(target in filesystem.list(base))
  }

  @Test
  fun `atomicMove source does not exist`() {
    val source = base / "source"
    val target = base / "target"
    assertFailsWith<IOException> {
      filesystem.atomicMove(source, target)
    }
  }

  @Test
  fun `atomicMove source is file and target is directory`() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    filesystem.createDirectory(target)
    assertFailsWith<IOException> {
      filesystem.atomicMove(source, target)
    }
  }

  @Test
  @Ignore // somehow the behaviour is different on windows
  fun `atomicMove source is directory and target is file`() {
    val source = base / "source"
    filesystem.createDirectory(source)
    val target = base / "target"
    target.writeUtf8("hello, world!")
    assertFailsWith<IOException> {
      filesystem.atomicMove(source, target)
    }
  }

  @Test
  fun `copy file`() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    filesystem.copy(source, target)
    assertTrue(target in filesystem.list(base))
    assertEquals("hello, world!", source.readUtf8())
    assertEquals("hello, world!", target.readUtf8())
  }

  @Test
  fun `copy source does not exist`() {
    val source = base / "source"
    val target = base / "target"
    assertFailsWith<IOException> {
      filesystem.copy(source, target)
    }
    assertFalse(target in filesystem.list(base))
  }

  @Test
  fun `copy target is clobbered`() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    target.writeUtf8("this file will be clobbered!")
    filesystem.copy(source, target)
    assertTrue(target in filesystem.list(base))
    assertEquals("hello, world!", target.readUtf8())
  }

  @Test
  fun `delete file`() {
    val path = base / "delete-file"
    path.writeUtf8("delete me")
    filesystem.delete(path)
    assertTrue(path !in filesystem.list(base))
  }

  @Test
  fun `delete empty directory`() {
    val path = base / "delete-empty-directory"
    filesystem.createDirectory(path)
    filesystem.delete(path)
    assertTrue(path !in filesystem.list(base))
  }

  @Test
  fun `delete fails on no such file`() {
    val path = base / "no-such-file"
    assertFailsWith<IOException> {
      filesystem.delete(path)
    }
  }

  @Test
  fun `delete fails on nonempty directory`() {
    val path = base / "non-empty-directory"
    filesystem.createDirectory(path)
    (path / "file.txt").writeUtf8("inside directory")
    assertFailsWith<IOException> {
      filesystem.delete(path)
    }
  }

  @Test
  fun `file metadata`() {
    val minTime = clock.now().minFileSystemTime()
    val path = base / "file-metadata"
    path.writeUtf8("hello, world!")
    val maxTime = clock.now().maxFileSystemTime()

    val metadata = filesystem.metadata(path)
    assertTrue(metadata.isRegularFile)
    assertFalse(metadata.isDirectory)
    assertEquals(13, metadata.size)
    assertTrue { (metadata.createdAt ?: minTime) in minTime..maxTime }
    assertTrue { (metadata.lastModifiedAt ?: minTime) in minTime..maxTime }
    assertTrue { (metadata.lastAccessedAt ?: minTime) in minTime..maxTime }
  }

  @Test
  fun `directory metadata`() {
    val minTime = clock.now().minFileSystemTime()
    val path = base / "directory-metadata"
    filesystem.createDirectory(path)
    val maxTime = clock.now().maxFileSystemTime()

    val metadata = filesystem.metadata(path)
    assertFalse(metadata.isRegularFile)
    assertTrue(metadata.isDirectory)
    // Note that the size check is omitted; we'd expect null but the JVM returns values like 64.
    assertTrue { (metadata.createdAt ?: minTime) in minTime..maxTime }
    assertTrue { (metadata.lastModifiedAt ?: minTime) in minTime..maxTime }
    assertTrue { (metadata.lastAccessedAt ?: minTime) in minTime..maxTime }
  }

  @Test
  fun `absent metadata`() {
    val path = base / "no-such-file"
    assertFailsWith<IOException> {
      filesystem.metadata(path)
    }
  }

  private fun randomToken() = Random.nextBytes(16).toByteString().hex()

  fun Path.readUtf8(): String {
    val source = filesystem.source(this).buffer()
    try {
      return source.readUtf8()
    } finally {
      source.close()
    }
  }

  fun Path.writeUtf8(string: String) {
    val sink = filesystem.sink(this).buffer()
    try {
      sink.writeUtf8(string)
    } finally {
      sink.close()
    }
  }

  /**
   * Returns the earliest filesystem time that could be recorded for an event occurring at this
   * instant. This truncates fractional seconds because most host filesystems do not use precise
   * timestamps for file metadata.
   */
  private fun Instant.minFileSystemTime(): Instant {
    return Instant.fromEpochSeconds(epochSeconds)
  }

  /**
   * Returns the latest filesystem time that could be recorded for an event occurring at this
   * instant. This adds 2 seconds and truncates fractional seconds because filesystems may defer
   * assigning the timestamp.
   *
   * https://docs.microsoft.com/en-us/windows/win32/sysinfo/file-times
   */
  private fun Instant.maxFileSystemTime(): Instant {
    return Instant.fromEpochSeconds(plus(2.seconds).epochSeconds)
  }
}
