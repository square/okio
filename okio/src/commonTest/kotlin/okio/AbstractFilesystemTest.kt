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

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

/** This test assumes that okio-files/ is the current working directory when executed. */
@ExperimentalTime
@ExperimentalFilesystem
abstract class AbstractFilesystemTest(
  val clock: Clock,
  val filesystem: Filesystem,
  val windowsLimitations: Boolean,
  temporaryDirectory: Path
) {
  val base: Path = temporaryDirectory / "${this::class.simpleName}-${randomToken()}"
  private val isJs = filesystem::class.simpleName?.startsWith("NodeJs") ?: false

  @BeforeTest
  fun setUp() {
    filesystem.createDirectory(base)
  }

  @Test
  fun canonicalizeDotReturnsCurrentWorkingDirectory() {
    if (filesystem is FakeFilesystem || filesystem is ForwardingFilesystem) return
    val cwd = filesystem.canonicalize(".".toPath())
    val cwdString = cwd.toString()
    assertTrue(cwdString) {
      cwdString.endsWith("okio${Path.directorySeparator}okio") ||
        cwdString.endsWith("${Path.directorySeparator}okio-parent-okio-test") || // JS
        cwdString.contains("/CoreSimulator/Devices/") || // iOS simulator.
        cwdString == "/" // Android emulator.
    }
  }

  @Test
  fun canonicalizeNoSuchFile() {
    assertFailsWith<FileNotFoundException> {
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
  fun listNoSuchDirectory() {
    assertFailsWith<FileNotFoundException> {
      filesystem.list(base / "no-such-directory")
    }
  }

  @Test
  fun listFile() {
    val target = base / "list"
    target.writeUtf8("hello, world!")
    assertFailsWith<IOException> {
      filesystem.list(target)
    }
  }

  @Test
  fun fileSourceNoSuchDirectory() {
    assertFailsWith<FileNotFoundException> {
      filesystem.source(base / "no-such-directory" / "file")
    }
  }

  @Test
  fun fileSource() {
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
  fun fileSink() {
    val path = base / "file-sink"
    val sink = filesystem.sink(path)
    val buffer = Buffer().writeUtf8("hello, world!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in filesystem.list(base))
    assertEquals(0, buffer.size)
    assertEquals("hello, world!", path.readUtf8())
  }

  @Test
  fun appendingSinkAppendsToExistingFile() {
    val path = base / "appending-sink-appends-to-existing-file"
    path.writeUtf8("hello, world!\n")
    val sink = filesystem.appendingSink(path)
    val buffer = Buffer().writeUtf8("this is added later!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in filesystem.list(base))
    assertEquals("hello, world!\nthis is added later!", path.readUtf8())
  }

  @Test
  fun appendingSinkDoesNotImpactExistingFile() {
    val path = base / "appending-sink-does-not-impact-existing-file"
    path.writeUtf8("hello, world!\n")
    val sink = filesystem.appendingSink(path)
    assertEquals("hello, world!\n", path.readUtf8())
    sink.close()
    assertEquals("hello, world!\n", path.readUtf8())
  }

  @Test
  fun appendingSinkCreatesNewFile() {
    val path = base / "appending-sink-creates-new-file"
    val sink = filesystem.appendingSink(path)
    val buffer = Buffer().writeUtf8("this is all there is!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in filesystem.list(base))
    assertEquals("this is all there is!", path.readUtf8())
  }

  @Test
  fun fileSinkFlush() {
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
  fun fileSinkNoSuchDirectory() {
    assertFailsWith<FileNotFoundException> {
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
  fun createDirectoryAlreadyExists() {
    val path = base / "already-exists"
    filesystem.createDirectory(path)
    assertFailsWith<IOException> {
      filesystem.createDirectory(path)
    }
  }

  @Test
  fun createDirectoryParentDirectoryDoesNotExist() {
    val path = base / "no-such-directory" / "created"
    assertFailsWith<IOException> {
      filesystem.createDirectory(path)
    }
  }

  @Test
  fun createDirectoriesSingle() {
    val path = base / "create-directories-single"
    filesystem.createDirectories(path)
    assertTrue(path in filesystem.list(base))
    assertTrue(filesystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesAlreadyExists() {
    val path = base / "already-exists"
    filesystem.createDirectory(path)
    filesystem.createDirectories(path)
    assertTrue(filesystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesParentDirectoryDoesNotExist() {
    filesystem.createDirectories(base / "a" / "b" / "c")
    assertTrue(base / "a" in filesystem.list(base))
    assertTrue(base / "a" / "b" in filesystem.list(base / "a"))
    assertTrue(base / "a" / "b" / "c" in filesystem.list(base / "a" / "b"))
    assertTrue(filesystem.metadata(base / "a" / "b" / "c").isDirectory)
  }

  @Test
  fun createDirectoriesParentIsFile() {
    val file = base / "simple-file"
    file.writeUtf8("just a file")
    assertFailsWith<IOException> {
      filesystem.createDirectories(file / "child")
    }
  }

  @Test
  fun atomicMoveFile() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    filesystem.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in filesystem.list(base))
    assertTrue(target in filesystem.list(base))
  }

  @Test
  fun atomicMoveDirectory() {
    val source = base / "source"
    filesystem.createDirectory(source)
    val target = base / "target"
    filesystem.atomicMove(source, target)
    assertTrue(source !in filesystem.list(base))
    assertTrue(target in filesystem.list(base))
  }

  @Test
  fun atomicMoveSourceIsTarget() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    filesystem.atomicMove(source, source)
    assertEquals("hello, world!", source.readUtf8())
    assertTrue(source in filesystem.list(base))
  }

  @Test
  fun atomicMoveClobberExistingFile() {
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
  fun atomicMoveSourceDoesNotExist() {
    val source = base / "source"
    val target = base / "target"
    assertFailsWith<FileNotFoundException> {
      filesystem.atomicMove(source, target)
    }
  }

  @Test
  fun atomicMoveSourceIsFileAndTargetIsDirectory() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    filesystem.createDirectory(target)
    assertFailsWith<IOException> {
      filesystem.atomicMove(source, target)
    }
  }

  @Test
  fun atomicMoveSourceIsDirectoryAndTargetIsFile() {
    val source = base / "source"
    filesystem.createDirectory(source)
    val target = base / "target"
    target.writeUtf8("hello, world!")
    expectIOExceptionOnEverythingButWindows {
      filesystem.atomicMove(source, target)
    }
  }

  @Test
  fun copyFile() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    filesystem.copy(source, target)
    assertTrue(target in filesystem.list(base))
    assertEquals("hello, world!", source.readUtf8())
    assertEquals("hello, world!", target.readUtf8())
  }

  @Test
  fun copySourceDoesNotExist() {
    val source = base / "source"
    val target = base / "target"
    assertFailsWith<FileNotFoundException> {
      filesystem.copy(source, target)
    }
    assertFalse(target in filesystem.list(base))
  }

  @Test
  fun copyTargetIsClobbered() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    target.writeUtf8("this file will be clobbered!")
    filesystem.copy(source, target)
    assertTrue(target in filesystem.list(base))
    assertEquals("hello, world!", target.readUtf8())
  }

  @Test
  fun deleteFile() {
    val path = base / "delete-file"
    path.writeUtf8("delete me")
    filesystem.delete(path)
    assertTrue(path !in filesystem.list(base))
  }

  @Test
  fun deleteEmptyDirectory() {
    val path = base / "delete-empty-directory"
    filesystem.createDirectory(path)
    filesystem.delete(path)
    assertTrue(path !in filesystem.list(base))
  }

  @Test
  fun deleteFailsOnNoSuchFile() {
    val path = base / "no-such-file"
    // TODO(jwilson): fix Windows to throw FileNotFoundException on deleting an absent file.
    if (windowsLimitations) {
      assertFailsWith<IOException> {
        filesystem.delete(path)
      }
    } else {
      assertFailsWith<FileNotFoundException> {
        filesystem.delete(path)
      }
    }
  }

  @Test
  fun deleteFailsOnNonemptyDirectory() {
    val path = base / "non-empty-directory"
    filesystem.createDirectory(path)
    (path / "file.txt").writeUtf8("inside directory")
    assertFailsWith<IOException> {
      filesystem.delete(path)
    }
  }

  @Test
  fun deleteRecursivelyFile() {
    val path = base / "delete-recursively-file"
    path.writeUtf8("delete me")
    filesystem.deleteRecursively(path)
    assertTrue(path !in filesystem.list(base))
  }

  @Test
  fun deleteRecursivelyEmptyDirectory() {
    val path = base / "delete-recursively-empty-directory"
    filesystem.createDirectory(path)
    filesystem.deleteRecursively(path)
    assertTrue(path !in filesystem.list(base))
  }

  @Test
  fun deleteRecursivelyFailsOnNoSuchFile() {
    val path = base / "no-such-file"
    assertFailsWith<FileNotFoundException> {
      filesystem.deleteRecursively(path)
    }
  }

  @Test
  fun deleteRecursivelyNonemptyDirectory() {
    val path = base / "delete-recursively-non-empty-directory"
    filesystem.createDirectory(path)
    (path / "file.txt").writeUtf8("inside directory")
    filesystem.deleteRecursively(path)
    assertTrue(path !in filesystem.list(base))
    assertTrue((path / "file.txt") !in filesystem.list(base))
  }

  @Test
  fun deleteRecursivelyDeepHierarchy() {
    filesystem.createDirectory(base / "a")
    filesystem.createDirectory(base / "a" / "b")
    filesystem.createDirectory(base / "a" / "b" / "c")
    (base / "a" / "b" / "c" / "d.txt").writeUtf8("inside deep hierarchy")
    filesystem.deleteRecursively(base / "a")
    assertEquals(filesystem.list(base), listOf())
  }

  @Test
  fun fileMetadata() {
    val minTime = clock.now().minFileSystemTime()
    val path = base / "file-metadata"
    path.writeUtf8("hello, world!")
    val maxTime = clock.now().maxFileSystemTime()

    val metadata = filesystem.metadata(path)
    assertTrue(metadata.isRegularFile)
    assertFalse(metadata.isDirectory)
    assertEquals(13, metadata.size)
    assertInRange(metadata.createdAt, minTime, maxTime)
    assertInRange(metadata.lastModifiedAt, minTime, maxTime)
    assertInRange(metadata.lastAccessedAt, minTime, maxTime)
  }

  @Test
  fun directoryMetadata() {
    val minTime = clock.now().minFileSystemTime()
    val path = base / "directory-metadata"
    filesystem.createDirectory(path)
    val maxTime = clock.now().maxFileSystemTime()

    val metadata = filesystem.metadata(path)
    assertFalse(metadata.isRegularFile)
    assertTrue(metadata.isDirectory)
    // Note that the size check is omitted; we'd expect null but the JVM returns values like 64.
    assertInRange(metadata.createdAt, minTime, maxTime)
    assertInRange(metadata.lastModifiedAt, minTime, maxTime)
    assertInRange(metadata.lastAccessedAt, minTime, maxTime)
  }

  @Test
  fun absentMetadataOrNull() {
    val path = base / "no-such-file"
    assertNull(filesystem.metadataOrNull(path))
  }

  @Test
  @Ignore
  fun inaccessibleMetadata() {
    // TODO(swankjesse): configure a test directory in CI that exists, but that this process doesn't
    //     have permission to read metadata of. Perhaps a file in another user's /home directory?
  }

  @Test
  fun absentMetadata() {
    val path = base / "no-such-file"
    assertFailsWith<FileNotFoundException> {
      filesystem.metadata(path)
    }
  }

  @Test
  fun fileExists() {
    val path = base / "file-exists"
    assertFalse(filesystem.exists(path))
    path.writeUtf8("hello, world!")
    assertTrue(filesystem.exists(path))
  }

  @Test
  fun directoryExists() {
    val path = base / "directory-exists"
    assertFalse(filesystem.exists(path))
    filesystem.createDirectory(path)
    assertTrue(filesystem.exists(path))
  }

  @Test
  fun deleteOpenForWritingFailsOnWindows() {
    val file = base / "file.txt"
    expectIOExceptionOnWindows(exceptJs = true) {
      filesystem.sink(file).use {
        filesystem.delete(file)
      }
    }
  }

  @Test
  fun deleteOpenForReadingFailsOnWindows() {
    val file = base / "file.txt"
    file.writeUtf8("abc")
    expectIOExceptionOnWindows(exceptJs = true) {
      filesystem.source(file).use {
        filesystem.delete(file)
      }
    }
  }

  @Test
  fun renameSourceIsOpenFailsOnWindows() {
    val from = base / "from.txt"
    val to = base / "to.txt"
    from.writeUtf8("source file")
    to.writeUtf8("target file")
    expectIOExceptionOnWindows(exceptJs = true) {
      filesystem.source(from).use {
        filesystem.atomicMove(from, to)
      }
    }
  }

  @Test
  fun renameTargetIsOpenFailsOnWindows() {
    val from = base / "from.txt"
    val to = base / "to.txt"
    from.writeUtf8("source file")
    to.writeUtf8("target file")
    expectIOExceptionOnWindows {
      filesystem.source(to).use {
        filesystem.atomicMove(from, to)
      }
    }
  }

  @Test
  fun deleteContentsOfParentOfFileOpenForReadingFailsOnWindows() {
    val parentA = (base / "a")
    filesystem.createDirectory(parentA)
    val parentAB = parentA / "b"
    filesystem.createDirectory(parentAB)
    val parentABC = parentAB / "c"
    filesystem.createDirectory(parentABC)
    val file = parentABC / "file.txt"
    file.writeUtf8("child file")
    expectIOExceptionOnWindows {
      filesystem.source(file).use {
        filesystem.delete(file)
        filesystem.delete(parentABC)
        filesystem.delete(parentAB)
        filesystem.delete(parentA)
      }
    }
  }

  private fun expectIOExceptionOnWindows(exceptJs: Boolean = false, block: () -> Unit) {
    val expectCrash = windowsLimitations && (!isJs || !exceptJs)
    try {
      block()
      assertFalse(expectCrash)
    } catch (_: IOException) {
      assertTrue(expectCrash)
    }
  }

  private fun expectIOExceptionOnEverythingButWindows(block: () -> Unit) {
    try {
      block()
      assertTrue(windowsLimitations)
    } catch (e: IOException) {
      assertFalse(windowsLimitations)
    }
  }

  private fun randomToken() = Random.nextBytes(16).toByteString(0, 16).hex()

  fun Path.readUtf8(): String {
    return filesystem.source(this).buffer().use {
      it.readUtf8()
    }
  }

  fun Path.writeUtf8(string: String) {
    filesystem.sink(this).buffer().use {
      it.writeUtf8(string)
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

  private fun assertInRange(sampled: Instant?, minTime: Instant, maxTime: Instant) {
    if (sampled == null) return
    assertTrue("expected $sampled in $minTime..$maxTime") { sampled in minTime..maxTime }
  }
}
