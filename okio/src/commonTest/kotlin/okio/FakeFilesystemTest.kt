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
package okio

import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@ExperimentalTime
@ExperimentalFilesystem
class FakeWindowsFilesystemTest : FakeFilesystemTest(
  clock = FakeClock(),
  windowsLimitations = true,
  temporaryDirectory = "C:\\".toPath(),
)

@ExperimentalTime
@ExperimentalFilesystem
class FakeUnixFilesystemTest : FakeFilesystemTest(
  clock = FakeClock(),
  windowsLimitations = false,
  temporaryDirectory = "/".toPath(),
)

@ExperimentalTime
@ExperimentalFilesystem
abstract class FakeFilesystemTest internal constructor(
  clock: FakeClock,
  windowsLimitations: Boolean,
  temporaryDirectory: Path
) : AbstractFilesystemTest(
  clock = clock,
  filesystem = FakeFilesystem(windowsLimitations, clock = clock),
  windowsLimitations = windowsLimitations,
  temporaryDirectory = temporaryDirectory
) {
  private val fakeFilesystem: FakeFilesystem = filesystem as FakeFilesystem
  private val fakeClock: FakeClock = clock

  @Test
  fun openPathsIncludesOpenSink() {
    val openPath = base / "open-file"
    val sink = filesystem.sink(openPath)
    assertEquals(openPath, fakeFilesystem.openPaths.single())
    sink.close()
    assertTrue(fakeFilesystem.openPaths.isEmpty())
  }

  @Test
  fun openPathsIncludesOpenSource() {
    val openPath = base / "open-file"
    openPath.writeUtf8("hello, world!")
    assertTrue(fakeFilesystem.openPaths.isEmpty())
    val source = filesystem.source(openPath)
    assertEquals(openPath, fakeFilesystem.openPaths.single())
    source.close()
    assertTrue(fakeFilesystem.openPaths.isEmpty())
  }

  @Test
  fun allPathsIncludesFile() {
    val file = base / "all-files-includes-file"
    file.writeUtf8("hello, world!")
    assertEquals(fakeFilesystem.allPaths, setOf(base, file))
  }

  @Test
  fun allPathsIncludesDirectory() {
    val dir = base / "all-files-includes-directory"
    filesystem.createDirectory(dir)
    assertEquals(fakeFilesystem.allPaths, setOf(base, dir))
  }

  @Test
  fun allPathsDoesNotIncludeDeletedFile() {
    val file = base / "all-files-does-not-include-deleted-file"
    file.writeUtf8("hello, world!")
    filesystem.delete(file)
    assertEquals(fakeFilesystem.allPaths, setOf(base))
  }

  @Test
  fun allPathsDoesNotIncludeDeletedOpenFile() {
    if (windowsLimitations) return // Can't delete open files with Windows' limitations.

    val file = base / "all-files-does-not-include-deleted-open-file"
    val sink = filesystem.sink(file)
    assertEquals(fakeFilesystem.allPaths, setOf(base, file))
    filesystem.delete(file)
    assertEquals(fakeFilesystem.allPaths, setOf(base))
    sink.close()
  }

  @Test
  fun fileLastAccessedTime() {
    val path = base / "file-last-accessed-time"

    fakeClock.sleep(1.minutes)
    path.writeUtf8("hello, world!")
    val createdAt = clock.now()

    fakeClock.sleep(1.minutes)
    path.writeUtf8("hello again!")
    val modifiedAt = clock.now()

    fakeClock.sleep(1.minutes)
    path.readUtf8()
    val accessedAt = clock.now()

    val metadata = filesystem.metadata(path)
    assertEquals(createdAt, metadata.createdAt)
    assertEquals(modifiedAt, metadata.lastModifiedAt)
    assertEquals(accessedAt, metadata.lastAccessedAt)
  }

  @Test
  fun directoryLastAccessedTime() {
    val path = base / "directory-last-accessed-time"

    fakeClock.sleep(1.minutes)
    filesystem.createDirectory(path)
    val createdAt = clock.now()

    fakeClock.sleep(1.minutes)
    (path / "child").writeUtf8("hello world!")
    val modifiedAt = clock.now()

    fakeClock.sleep(1.minutes)
    filesystem.list(path)
    val accessedAt = clock.now()

    val metadata = filesystem.metadata(path)
    assertEquals(createdAt, metadata.createdAt)
    assertEquals(modifiedAt, metadata.lastModifiedAt)
    assertEquals(accessedAt, metadata.lastAccessedAt)
  }

  @Test
  fun checkNoOpenFilesThrowsOnOpenSource() {
    val path = base / "check-no-open-files-open-source"
    path.writeUtf8("hello, world!")
    val exception = filesystem.source(path).use { source ->
      assertFailsWith<IllegalStateException> {
        fakeFilesystem.checkNoOpenFiles()
      }
    }

    assertEquals(
      """
      |expected 0 open files, but found:
      |    $path
      """.trimMargin(),
      exception.message
    )
    assertEquals("file opened for reading here", exception.cause?.message)

    // Now that the source is closed this is safe.
    fakeFilesystem.checkNoOpenFiles()
  }

  @Test
  fun checkNoOpenFilesThrowsOnOpenSink() {
    val path = base / "check-no-open-files-open-sink"
    val exception = filesystem.sink(path).use { source ->
      assertFailsWith<IllegalStateException> {
        fakeFilesystem.checkNoOpenFiles()
      }
    }

    assertEquals(
      """
      |expected 0 open files, but found:
      |    $path
      """.trimMargin(),
      exception.message
    )
    assertEquals("file opened for writing here", exception.cause?.message)

    // Now that the source is closed this is safe.
    fakeFilesystem.checkNoOpenFiles()
  }

  @Test
  fun createDirectoriesForVolumeLetterRoot() {
    val path = "X:\\".toPath()
    filesystem.createDirectories(path)
    assertTrue(filesystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForChildOfVolumeLetterRoot() {
    val path = "X:\\path".toPath()
    filesystem.createDirectories(path)
    assertTrue(filesystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForUnixRoot() {
    val path = "/".toPath()
    filesystem.createDirectories(path)
    assertTrue(filesystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForChildOfUnixRoot() {
    val path = "/path".toPath()
    filesystem.createDirectories(path)
    assertTrue(filesystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForUncRoot() {
    val path = "\\\\server".toPath()
    filesystem.createDirectories(path)
    assertTrue(filesystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesForChildOfUncRoot() {
    val path = "\\\\server\\project".toPath()
    filesystem.createDirectories(path)
    assertTrue(filesystem.metadata(path).isDirectory)
  }

  @Test
  fun workingDirectoryMustBeAbsolute() {
    val exception = assertFailsWith<IllegalArgumentException> {
      FakeFilesystem(workingDirectory = "some/relative/path".toPath())
    }
    assertEquals("expected an absolute path but was some/relative/path", exception.message)
  }

  @Test
  fun metadataForRootsGeneratedOnDemand() {
    assertTrue(filesystem.metadata("X:\\".toPath()).isDirectory)
    assertTrue(filesystem.metadata("/".toPath()).isDirectory)
    assertTrue(filesystem.metadata("\\\\server".toPath()).isDirectory)
  }
}
