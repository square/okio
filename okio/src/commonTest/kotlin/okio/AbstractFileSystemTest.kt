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

import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.seconds
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

/** This test assumes that okio-files/ is the current working directory when executed. */
@ExperimentalTime
@ExperimentalFileSystem
abstract class AbstractFileSystemTest(
  val clock: Clock,
  val fileSystem: FileSystem,
  val windowsLimitations: Boolean,
  val allowClobberingEmptyDirectories: Boolean,
  temporaryDirectory: Path
) {
  val base: Path = temporaryDirectory / "${this::class.simpleName}-${randomToken()}"
  private val isJs = fileSystem::class.simpleName?.startsWith("NodeJs") ?: false

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test
  fun canonicalizeDotReturnsCurrentWorkingDirectory() {
    if (fileSystem is FakeFileSystem || fileSystem is ForwardingFileSystem) return
    val cwd = fileSystem.canonicalize(".".toPath())
    val cwdString = cwd.toString()
    assertTrue(cwdString) {
      cwdString.endsWith("okio${Path.DIRECTORY_SEPARATOR}okio") ||
        cwdString.endsWith("${Path.DIRECTORY_SEPARATOR}okio-parent-okio-test") || // JS
        cwdString.contains("/CoreSimulator/Devices/") || // iOS simulator.
        cwdString == "/" // Android emulator.
    }
  }

  @Test
  fun currentWorkingDirectoryIsADirectory() {
    val metadata = fileSystem.metadata(".".toPath())
    assertTrue(metadata.isDirectory)
    assertFalse(metadata.isRegularFile)
  }

  @Test
  fun canonicalizeNoSuchFile() {
    assertFailsWith<FileNotFoundException> {
      fileSystem.canonicalize(base / "no-such-file")
    }
  }

  @Test
  fun list() {
    val target = base / "list"
    target.writeUtf8("hello, world!")
    val entries = fileSystem.list(base)
    assertTrue(entries.toString()) { target in entries }
  }

  @Test
  fun listOnRelativePathReturnsRelativePaths() {
    // Make sure there's always at least one file so our assertion is useful.
    if (fileSystem is FakeFileSystem) {
      val workingDirectory = "/directory".toPath()
      fileSystem.createDirectory(workingDirectory)
      fileSystem.workingDirectory = workingDirectory
      fileSystem.write("a.txt".toPath()) {
        writeUtf8("hello, world!")
      }
    }

    val entries = fileSystem.list(".".toPath())
    assertTrue(entries.toString()) { entries.isNotEmpty() && entries.all { it.isRelative } }
  }

  @Test
  fun listResultsAreSorted() {
    val fileA = base / "a"
    val fileB = base / "b"
    val fileC = base / "c"
    val fileD = base / "d"

    // Create files in a different order than the sorted order, so a file system that returns files
    // in creation-order or reverse-creation order won't pass by accident.
    fileD.writeUtf8("fileD")
    fileB.writeUtf8("fileB")
    fileC.writeUtf8("fileC")
    fileA.writeUtf8("fileA")

    val entries = fileSystem.list(base)
    assertEquals(entries, listOf(fileA, fileB, fileC, fileD))
  }

  @Test
  fun listNoSuchDirectory() {
    assertFailsWith<FileNotFoundException> {
      fileSystem.list(base / "no-such-directory")
    }
  }

  @Test
  fun listFile() {
    val target = base / "list"
    target.writeUtf8("hello, world!")
    assertFailsWith<IOException> {
      fileSystem.list(target)
    }
  }

  @Test
  fun fileSourceNoSuchDirectory() {
    assertFailsWith<FileNotFoundException> {
      fileSystem.source(base / "no-such-directory" / "file")
    }
  }

  @Test
  fun fileSource() {
    val path = base / "file-source"
    path.writeUtf8("hello, world!")

    val source = fileSystem.source(path)
    val buffer = Buffer()
    assertTrue(source.read(buffer, 100L) == 13L)
    assertEquals(-1L, source.read(buffer, 100L))
    assertEquals("hello, world!", buffer.readUtf8())
    source.close()
  }

  @Test
  fun readPath() {
    val path = base / "read-path"
    val string = "hello, read with a Path"
    path.writeUtf8(string)

    val result = fileSystem.read(path) {
      assertEquals("hello", readUtf8(5))
      assertEquals(", read with ", readUtf8(12))
      assertEquals("a Path", readUtf8())
      return@read "success"
    }
    assertEquals("success", result)
  }

  @Test
  fun fileSink() {
    val path = base / "file-sink"
    val sink = fileSystem.sink(path)
    val buffer = Buffer().writeUtf8("hello, world!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in fileSystem.list(base))
    assertEquals(0, buffer.size)
    assertEquals("hello, world!", path.readUtf8())
  }

  @Test
  fun writePath() {
    val path = base / "write-path"
    val content = fileSystem.write(path) {
      val string = "hello, write with a Path"
      writeUtf8(string)
      return@write string
    }
    assertTrue(path in fileSystem.list(base))
    assertEquals(content, path.readUtf8())
  }

  @Test
  fun appendingSinkAppendsToExistingFile() {
    val path = base / "appending-sink-appends-to-existing-file"
    path.writeUtf8("hello, world!\n")
    val sink = fileSystem.appendingSink(path)
    val buffer = Buffer().writeUtf8("this is added later!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in fileSystem.list(base))
    assertEquals("hello, world!\nthis is added later!", path.readUtf8())
  }

  @Test
  fun appendingSinkDoesNotImpactExistingFile() {
    if (fileSystem is FakeFileSystem && !fileSystem.allowReadsWhileWriting) return

    val path = base / "appending-sink-does-not-impact-existing-file"
    path.writeUtf8("hello, world!\n")
    val sink = fileSystem.appendingSink(path)
    assertEquals("hello, world!\n", path.readUtf8())
    sink.close()
    assertEquals("hello, world!\n", path.readUtf8())
  }

  @Test
  fun appendingSinkCreatesNewFile() {
    val path = base / "appending-sink-creates-new-file"
    val sink = fileSystem.appendingSink(path)
    val buffer = Buffer().writeUtf8("this is all there is!")
    sink.write(buffer, buffer.size)
    sink.close()
    assertTrue(path in fileSystem.list(base))
    assertEquals("this is all there is!", path.readUtf8())
  }

  @Test
  fun fileSinkFlush() {
    if (fileSystem is FakeFileSystem && !fileSystem.allowReadsWhileWriting) return

    val path = base / "file-sink"
    val sink = fileSystem.sink(path)

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
      fileSystem.sink(base / "no-such-directory" / "file")
    }
  }

  @Test
  fun createDirectory() {
    val path = base / "create-directory"
    fileSystem.createDirectory(path)
    assertTrue(path in fileSystem.list(base))
  }

  @Test
  fun createDirectoryAlreadyExists() {
    val path = base / "already-exists"
    fileSystem.createDirectory(path)
    assertFailsWith<IOException> {
      fileSystem.createDirectory(path)
    }
  }

  @Test
  fun createDirectoryParentDirectoryDoesNotExist() {
    val path = base / "no-such-directory" / "created"
    assertFailsWith<IOException> {
      fileSystem.createDirectory(path)
    }
  }

  @Test
  fun createDirectoriesSingle() {
    val path = base / "create-directories-single"
    fileSystem.createDirectories(path)
    assertTrue(path in fileSystem.list(base))
    assertTrue(fileSystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesAlreadyExists() {
    val path = base / "already-exists"
    fileSystem.createDirectory(path)
    fileSystem.createDirectories(path)
    assertTrue(fileSystem.metadata(path).isDirectory)
  }

  @Test
  fun createDirectoriesParentDirectoryDoesNotExist() {
    fileSystem.createDirectories(base / "a" / "b" / "c")
    assertTrue(base / "a" in fileSystem.list(base))
    assertTrue(base / "a" / "b" in fileSystem.list(base / "a"))
    assertTrue(base / "a" / "b" / "c" in fileSystem.list(base / "a" / "b"))
    assertTrue(fileSystem.metadata(base / "a" / "b" / "c").isDirectory)
  }

  @Test
  fun createDirectoriesParentIsFile() {
    val file = base / "simple-file"
    file.writeUtf8("just a file")
    assertFailsWith<IOException> {
      fileSystem.createDirectories(file / "child")
    }
  }

  @Test
  fun atomicMoveFile() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    fileSystem.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in fileSystem.list(base))
    assertTrue(target in fileSystem.list(base))
  }

  @Test
  fun atomicMoveDirectory() {
    val source = base / "source"
    fileSystem.createDirectory(source)
    val target = base / "target"
    fileSystem.atomicMove(source, target)
    assertTrue(source !in fileSystem.list(base))
    assertTrue(target in fileSystem.list(base))
  }

  @Test
  fun atomicMoveSourceIsTarget() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    fileSystem.atomicMove(source, source)
    assertEquals("hello, world!", source.readUtf8())
    assertTrue(source in fileSystem.list(base))
  }

  @Test
  fun atomicMoveClobberExistingFile() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    target.writeUtf8("this file will be clobbered!")
    fileSystem.atomicMove(source, target)
    assertEquals("hello, world!", target.readUtf8())
    assertTrue(source !in fileSystem.list(base))
    assertTrue(target in fileSystem.list(base))
  }

  @Test
  fun atomicMoveSourceDoesNotExist() {
    val source = base / "source"
    val target = base / "target"
    assertFailsWith<FileNotFoundException> {
      fileSystem.atomicMove(source, target)
    }
  }

  @Test
  fun atomicMoveSourceIsFileAndTargetIsDirectory() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    fileSystem.createDirectory(target)
    assertFailsWith<IOException> {
      fileSystem.atomicMove(source, target)
    }
  }

  @Test
  fun atomicMoveSourceIsDirectoryAndTargetIsFile() {
    val source = base / "source"
    fileSystem.createDirectory(source)
    val target = base / "target"
    target.writeUtf8("hello, world!")
    try {
      fileSystem.atomicMove(source, target)
      assertTrue(allowClobberingEmptyDirectories)
    } catch (e: IOException) {
      assertFalse(allowClobberingEmptyDirectories)
    }
  }

  @Test
  fun copyFile() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    fileSystem.copy(source, target)
    assertTrue(target in fileSystem.list(base))
    assertEquals("hello, world!", source.readUtf8())
    assertEquals("hello, world!", target.readUtf8())
  }

  @Test
  fun copySourceDoesNotExist() {
    val source = base / "source"
    val target = base / "target"
    assertFailsWith<FileNotFoundException> {
      fileSystem.copy(source, target)
    }
    assertFalse(target in fileSystem.list(base))
  }

  @Test
  fun copyTargetIsClobbered() {
    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    target.writeUtf8("this file will be clobbered!")
    fileSystem.copy(source, target)
    assertTrue(target in fileSystem.list(base))
    assertEquals("hello, world!", target.readUtf8())
  }

  @Test
  fun deleteFile() {
    val path = base / "delete-file"
    path.writeUtf8("delete me")
    fileSystem.delete(path)
    assertTrue(path !in fileSystem.list(base))
  }

  @Test
  fun deleteEmptyDirectory() {
    val path = base / "delete-empty-directory"
    fileSystem.createDirectory(path)
    fileSystem.delete(path)
    assertTrue(path !in fileSystem.list(base))
  }

  @Test
  fun deleteFailsOnNoSuchFile() {
    val path = base / "no-such-file"
    // TODO(jwilson): fix Windows to throw FileNotFoundException on deleting an absent file.
    if (windowsLimitations) {
      assertFailsWith<IOException> {
        fileSystem.delete(path)
      }
    } else {
      assertFailsWith<FileNotFoundException> {
        fileSystem.delete(path)
      }
    }
  }

  @Test
  fun deleteFailsOnNonemptyDirectory() {
    val path = base / "non-empty-directory"
    fileSystem.createDirectory(path)
    (path / "file.txt").writeUtf8("inside directory")
    assertFailsWith<IOException> {
      fileSystem.delete(path)
    }
  }

  @Test
  fun deleteRecursivelyFile() {
    val path = base / "delete-recursively-file"
    path.writeUtf8("delete me")
    fileSystem.deleteRecursively(path)
    assertTrue(path !in fileSystem.list(base))
  }

  @Test
  fun deleteRecursivelyEmptyDirectory() {
    val path = base / "delete-recursively-empty-directory"
    fileSystem.createDirectory(path)
    fileSystem.deleteRecursively(path)
    assertTrue(path !in fileSystem.list(base))
  }

  @Test
  fun deleteRecursivelyFailsOnNoSuchFile() {
    val path = base / "no-such-file"
    assertFailsWith<FileNotFoundException> {
      fileSystem.deleteRecursively(path)
    }
  }

  @Test
  fun deleteRecursivelyNonemptyDirectory() {
    val path = base / "delete-recursively-non-empty-directory"
    fileSystem.createDirectory(path)
    (path / "file.txt").writeUtf8("inside directory")
    fileSystem.deleteRecursively(path)
    assertTrue(path !in fileSystem.list(base))
    assertTrue((path / "file.txt") !in fileSystem.list(base))
  }

  @Test
  fun deleteRecursivelyDeepHierarchy() {
    fileSystem.createDirectory(base / "a")
    fileSystem.createDirectory(base / "a" / "b")
    fileSystem.createDirectory(base / "a" / "b" / "c")
    (base / "a" / "b" / "c" / "d.txt").writeUtf8("inside deep hierarchy")
    fileSystem.deleteRecursively(base / "a")
    assertEquals(fileSystem.list(base), listOf())
  }

  @Test
  fun fileMetadata() {
    val minTime = clock.now().minFileSystemTime()
    val path = base / "file-metadata"
    path.writeUtf8("hello, world!")
    val maxTime = clock.now().maxFileSystemTime()

    val metadata = fileSystem.metadata(path)
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
    fileSystem.createDirectory(path)
    val maxTime = clock.now().maxFileSystemTime()

    val metadata = fileSystem.metadata(path)
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
    assertNull(fileSystem.metadataOrNull(path))
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
      fileSystem.metadata(path)
    }
  }

  @Test
  fun fileExists() {
    val path = base / "file-exists"
    assertFalse(fileSystem.exists(path))
    path.writeUtf8("hello, world!")
    assertTrue(fileSystem.exists(path))
  }

  @Test
  fun directoryExists() {
    val path = base / "directory-exists"
    assertFalse(fileSystem.exists(path))
    fileSystem.createDirectory(path)
    assertTrue(fileSystem.exists(path))
  }

  @Test
  fun deleteOpenForWritingFailsOnWindows() {
    val file = base / "file.txt"
    expectIOExceptionOnWindows(exceptJs = true) {
      fileSystem.sink(file).use {
        fileSystem.delete(file)
      }
    }
  }

  @Test
  fun deleteOpenForReadingFailsOnWindows() {
    val file = base / "file.txt"
    file.writeUtf8("abc")
    expectIOExceptionOnWindows(exceptJs = true) {
      fileSystem.source(file).use {
        fileSystem.delete(file)
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
      fileSystem.source(from).use {
        fileSystem.atomicMove(from, to)
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
      fileSystem.source(to).use {
        fileSystem.atomicMove(from, to)
      }
    }
  }

  @Test
  fun deleteContentsOfParentOfFileOpenForReadingFailsOnWindows() {
    val parentA = (base / "a")
    fileSystem.createDirectory(parentA)
    val parentAB = parentA / "b"
    fileSystem.createDirectory(parentAB)
    val parentABC = parentAB / "c"
    fileSystem.createDirectory(parentABC)
    val file = parentABC / "file.txt"
    file.writeUtf8("child file")
    expectIOExceptionOnWindows {
      fileSystem.source(file).use {
        fileSystem.delete(file)
        fileSystem.delete(parentABC)
        fileSystem.delete(parentAB)
        fileSystem.delete(parentA)
      }
    }
  }

  @Test fun fileSourceCursorHappyPath() {
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

  @Test fun fileHandleSourceHappyPath() {
    if (!supportsFileHandle()) return // JVM only for now.

    val path = base / "file-handle-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }

    fileSystem.open(path, read = true).use { handle ->
      assertEquals(16L, handle.size())
      val buffer = Buffer()

      handle.source().use { source ->
        assertEquals(0L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals("abcd", buffer.readUtf8())
        assertEquals(4L, handle.position(source))
      }

      handle.source(fileOffset = 8L).use { source ->
        assertEquals(8L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals("ijkl", buffer.readUtf8())
        assertEquals(12L, handle.position(source))
      }

      handle.source(fileOffset = 16L).use { source ->
        assertEquals(16L, handle.position(source))
        assertEquals(-1L, source.read(buffer, 4L))
        assertEquals("", buffer.readUtf8())
        assertEquals(16L, handle.position(source))
      }
    }
  }

  @Test fun fileSourceCursorSeekBackwards() {
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

  @Test fun fileHandleSourceSeekBackwards() {
    if (!supportsFileHandle()) return // JVM only for now.

    val path = base / "file-handle-source-backwards"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    fileSystem.open(path, read = true).use { handle ->
      assertEquals(16L, handle.size())
      val buffer = Buffer()

      handle.source().use { source ->
        assertEquals(0L, handle.position(source))
        assertEquals(16L, source.read(buffer, 16L))
        assertEquals("abcdefghijklmnop", buffer.readUtf8())
        assertEquals(16L, handle.position(source))
      }

      handle.source(0L).use { source ->
        assertEquals(0L, handle.position(source))
        assertEquals(16L, source.read(buffer, 16L))
        assertEquals("abcdefghijklmnop", buffer.readUtf8())
        assertEquals(16L, handle.position(source))
      }
    }
  }

  @Test fun bufferedFileSourceCursorHappyPath() {
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

  @Test fun bufferedFileHandleSourceHappyPath() {
    if (!supportsFileHandle()) return // JVM only for now.

    val path = base / "file-handle-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }

    fileSystem.open(path, read = true).use { handle ->
      assertEquals(16L, handle.size())
      val buffer = Buffer()

      handle.source().buffer().use { source ->
        assertEquals(0L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals("abcd", buffer.readUtf8())
        assertEquals(4L, handle.position(source))
      }

      handle.source(fileOffset = 8L).buffer().use { source ->
        assertEquals(8L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals("ijkl", buffer.readUtf8())
        assertEquals(12L, handle.position(source))
      }

      handle.source(fileOffset = 16L).buffer().use { source ->
        assertEquals(16L, handle.position(source))
        assertEquals(-1L, source.read(buffer, 4L))
        assertEquals("", buffer.readUtf8())
        assertEquals(16L, handle.position(source))
      }
    }
  }

  @Test fun bufferedFileSourceCursorSeekBackwards() {
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

  @Test fun bufferedFileHandleSourceSeekBackwards() {
    if (!supportsFileHandle()) return // JVM only for now.

    val path = base / "file-handle-source-backwards"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    fileSystem.open(path, read = true).use { handle ->
      assertEquals(16L, handle.size())
      val buffer = Buffer()

      handle.source().buffer().use { source ->
        assertEquals(0L, handle.position(source))
        assertEquals(16L, source.read(buffer, 16L))
        assertEquals("abcdefghijklmnop", buffer.readUtf8())
        assertEquals(16L, handle.position(source))
      }

      handle.source(0L).buffer().use { source ->
        assertEquals(0L, handle.position(source))
        assertEquals(16L, source.read(buffer, 16L))
        assertEquals("abcdefghijklmnop", buffer.readUtf8())
        assertEquals(16L, handle.position(source))
      }
    }
  }

  @Test fun bufferedFileSourceSeekBeyondBuffer() {
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

  @Test fun sourceCursorWhenClosed() {
    val path = base / "file-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    val source = fileSystem.source(path)
    val cursor = source.cursor()!!
    source.close()

    assertClosedFailure {
      cursor.size()
    }
    assertClosedFailure {
      cursor.position()
    }
    assertClosedFailure {
      cursor.seek(0L)
    }
  }

  private fun assertClosedFailure(block: () -> Unit) {
    val exception = assertFails {
      block()
    }
    val exceptionType = exception::class.simpleName
    assertTrue(
      exceptionType == "IOException" ||
        exceptionType == "IllegalStateException" ||
        exceptionType == "ClosedChannelException",
      "unexpected exception: $exception"
    )
  }

  private fun supportsFileHandle(): Boolean {
    return when (fileSystem::class.simpleName) {
      "JvmSystemFileSystem", "FakeFileSystem" -> true
      else -> false
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

  fun Path.readUtf8(): String {
    return fileSystem.source(this).buffer().use {
      it.readUtf8()
    }
  }

  fun Path.writeUtf8(string: String) {
    fileSystem.sink(this).buffer().use {
      it.writeUtf8(string)
    }
  }

  /**
   * Returns the earliest file system time that could be recorded for an event occurring at this
   * instant. This truncates fractional seconds because most host file systems do not use precise
   * timestamps for file metadata.
   */
  private fun Instant.minFileSystemTime(): Instant {
    return Instant.fromEpochSeconds(epochSeconds)
  }

  /**
   * Returns the latest file system time that could be recorded for an event occurring at this
   * instant. This adds 2 seconds and truncates fractional seconds because file systems may defer
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
