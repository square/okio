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
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

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
  val base: Path = temporaryDirectory / "${this::class.simpleName}-${randomToken(16)}"
  private val isNodeJsFileSystem = fileSystem::class.simpleName?.startsWith("NodeJs") ?: false

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test
  fun canonicalizeDotReturnsCurrentWorkingDirectory() {
    if (fileSystem is FakeFileSystem || fileSystem is ForwardingFileSystem) return
    val cwd = fileSystem.canonicalize(".".toPath())
    val cwdString = cwd.toString()
    val slash = Path.DIRECTORY_SEPARATOR
    assertTrue(cwdString) {
      cwdString.endsWith("okio${slash}okio") ||
        cwdString.endsWith("${slash}okio-parent-okio-js-legacy-test") ||
        cwdString.endsWith("${slash}okio-parent-okio-js-ir-test") ||
        cwdString.endsWith("${slash}okio-parent-okio-nodefilesystem-js-ir-test") ||
        cwdString.endsWith("${slash}okio-parent-okio-nodefilesystem-js-legacy-test") ||
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
  fun listRecursivelyReturnsEmpty() {
    val entries = fileSystem.listRecursively(base)
    assertEquals(entries.toList(), listOf())
  }

  @Test
  fun listRecursivelyReturnsSingleFile() {
    val baseA = base / "a"
    baseA.writeUtf8("a")
    val entries = fileSystem.listRecursively(base)
    assertEquals(entries.toList(), listOf(baseA))
  }

  @Test
  fun listRecursivelyRecurses() {
    val baseA = base / "a"
    val baseAB = baseA / "b"
    baseA.createDirectory()
    baseAB.writeUtf8("ab")
    val entries = fileSystem.listRecursively(base)
    assertEquals(entries.toList(), listOf(baseA, baseAB))
  }

  @Test
  fun listRecursivelyNoSuchFile() {
    val baseA = base / "a"
    val sequence = fileSystem.listRecursively(baseA)
    assertFailsWith<FileNotFoundException> {
      sequence.first()
    }
  }

  /**
   * Not that this is different from `Files.walk` in java.nio which returns the argument if it is
   * not a directory.
   */
  @Test
  fun listRecursivelyNotADirectory() {
    val baseA = base / "a"
    baseA.writeUtf8("a")
    val sequence = fileSystem.listRecursively(baseA)
    val exception = assertFailsWith<IOException> {
      sequence.first()
    }
    assertTrue(exception !is FileNotFoundException)
  }

  @Test
  fun listRecursivelyIsBreadthFirst() {
    val baseA = base / "a"
    val baseB = base / "b"
    val baseA1 = baseA / "1"
    val baseA2 = baseA / "2"
    val baseB1 = baseB / "1"
    val baseB2 = baseB / "2"
    baseA.createDirectory()
    baseB.createDirectory()
    baseA1.writeUtf8("a1")
    baseA2.writeUtf8("a2")
    baseB1.writeUtf8("b1")
    baseB2.writeUtf8("b2")
    val entries = fileSystem.listRecursively(base)
    assertEquals(entries.toList(), listOf(baseA, baseB, baseA1, baseA2, baseB1, baseB2))
  }

  @Test
  fun listRecursivelyIsLazy() {
    val baseA = base / "a"
    val baseB = base / "b"
    baseA.createDirectory()
    baseB.createDirectory()
    val entries = fileSystem.listRecursively(base).iterator()
    assertEquals(baseA, entries.next())
    assertEquals(baseB, entries.next())
    val baseA1 = baseA / "1"
    val baseA2 = baseA / "2"
    baseA1.writeUtf8("a1")
    baseA2.writeUtf8("a2")
    assertEquals(baseA1, entries.next())
    assertEquals(baseA2, entries.next())
    val baseB1 = baseB / "1"
    val baseB2 = baseB / "2"
    baseB1.writeUtf8("b1")
    baseB2.writeUtf8("b2")
    assertEquals(baseB1, entries.next())
    assertEquals(baseB2, entries.next())
    assertFalse(entries.hasNext())
  }

  /**
   * This test creates directories that should be listed lazily, and then deletes them! The test
   * wants to confirm that the sequence is resilient to such changes.
   */
  @Test
  fun listRecursivelySilentlyIgnoresListFailures() {
    val baseA = base / "a"
    val baseB = base / "b"
    baseA.createDirectory()
    baseB.createDirectory()
    val entries = fileSystem.listRecursively(base).iterator()
    assertEquals(baseA, entries.next())
    assertEquals(baseB, entries.next())
    fileSystem.delete(baseA)
    fileSystem.delete(baseB)
    assertFalse(entries.hasNext())
  }

  @Test
  fun listRecursivelySequenceIterationsAreIndependent() {
    val sequence = fileSystem.listRecursively(base)
    val iterator1 = sequence.iterator()
    assertFalse(iterator1.hasNext())
    val baseA = base / "a"
    baseA.writeUtf8("a")
    val iterator2 = sequence.iterator()
    assertEquals(baseA, iterator2.next())
    assertFalse(iterator2.hasNext())
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

  /**
   * Write a file by concatenating three mechanisms, then read it in its entirety using three other
   * mechanisms. This is attempting to defend against unwanted use of Windows text mode.
   *
   * https://docs.microsoft.com/en-us/cpp/c-runtime-library/reference/fopen-wfopen?view=msvc-160
   */
  @Test
  fun fileSinkSpecialCharacters() {
    val path = base / "file-sink-special-characters"
    val content = "[ctrl-z: \u001A][newline: \n][crlf: \r\n]".encodeUtf8()

    fileSystem.write(path) {
      writeUtf8("FileSystem.write()\n")
      write(content)
    }

    fileSystem.openReadWrite(path).use { handle ->
      handle.sink(fileOffset = handle.size()).buffer().use { sink ->
        sink.writeUtf8("FileSystem.openReadWrite()\n")
        sink.write(content)
      }
    }

    fileSystem.appendingSink(path).buffer().use { sink ->
      sink.writeUtf8("FileSystem.appendingSink()\n")
      sink.write(content)
    }

    fileSystem.read(path) {
      assertEquals("FileSystem.write()", readUtf8LineStrict())
      assertEquals(content, readByteString(content.size.toLong()))
      assertEquals("FileSystem.openReadWrite()", readUtf8LineStrict())
      assertEquals(content, readByteString(content.size.toLong()))
      assertEquals("FileSystem.appendingSink()", readUtf8LineStrict())
      assertEquals(content, readByteString(content.size.toLong()))
      assertTrue(exhausted())
    }

    fileSystem.openReadWrite(path).use { handle ->
      handle.source().buffer().use { source ->
        assertEquals("FileSystem.write()", source.readUtf8LineStrict())
        assertEquals(content, source.readByteString(content.size.toLong()))
        assertEquals("FileSystem.openReadWrite()", source.readUtf8LineStrict())
        assertEquals(content, source.readByteString(content.size.toLong()))
        assertEquals("FileSystem.appendingSink()", source.readUtf8LineStrict())
        assertEquals(content, source.readByteString(content.size.toLong()))
        assertTrue(source.exhausted())
      }
    }

    fileSystem.openReadOnly(path).use { handle ->
      handle.source().buffer().use { source ->
        assertEquals("FileSystem.write()", source.readUtf8LineStrict())
        assertEquals(content, source.readByteString(content.size.toLong()))
        assertEquals("FileSystem.openReadWrite()", source.readUtf8LineStrict())
        assertEquals(content, source.readByteString(content.size.toLong()))
        assertEquals("FileSystem.appendingSink()", source.readUtf8LineStrict())
        assertEquals(content, source.readByteString(content.size.toLong()))
        assertTrue(source.exhausted())
      }
    }
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
    val minTime = clock.now()
    val path = base / "file-metadata"
    path.writeUtf8("hello, world!")
    val maxTime = clock.now()

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
    val minTime = clock.now()
    val path = base / "directory-metadata"
    fileSystem.createDirectory(path)
    val maxTime = clock.now()

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

  @Test fun fileHandleWriteAndRead() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-write-and-read"
    fileSystem.openReadWrite(path).use { handle ->

      handle.sink().buffer().use { sink ->
        sink.writeUtf8("abcdefghijklmnop")
      }

      handle.source().buffer().use { source ->
        assertEquals("abcde", source.readUtf8(5))
        assertEquals("fghijklmnop", source.readUtf8())
      }
    }
  }

  @Test fun fileHandleWriteAndOverwrite() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-write-and-overwrite"
    fileSystem.openReadWrite(path).use { handle ->

      handle.sink().buffer().use { sink ->
        sink.writeUtf8("abcdefghij")
      }

      handle.sink(fileOffset = handle.size() - 3).buffer().use { sink ->
        sink.writeUtf8("HIJKLMNOP")
      }

      handle.source().buffer().use { source ->
        assertEquals("abcdefgHIJKLMNOP", source.readUtf8())
      }
    }
  }

  @Test fun fileHandleWriteBeyondEnd() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-write-beyond-end"
    fileSystem.openReadWrite(path).use { handle ->

      handle.sink(fileOffset = 10).buffer().use { sink ->
        sink.writeUtf8("klmnop")
      }

      handle.source().buffer().use { source ->
        assertEquals("00000000000000000000", source.readByteString(10).hex())
        assertEquals("klmnop", source.readUtf8())
      }
    }
  }

  @Test fun fileHandleResizeSmaller() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-resize-smaller"
    fileSystem.openReadWrite(path).use { handle ->

      handle.sink().buffer().use { sink ->
        sink.writeUtf8("abcdefghijklmnop")
      }

      handle.resize(10)

      handle.source().buffer().use { source ->
        assertEquals("abcdefghij", source.readUtf8())
      }
    }
  }

  @Test fun fileHandleResizeLarger() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-resize-larger"
    fileSystem.openReadWrite(path).use { handle ->

      handle.sink().buffer().use { sink ->
        sink.writeUtf8("abcde")
      }

      handle.resize(15)

      handle.source().buffer().use { source ->
        assertEquals("abcde", source.readUtf8(5))
        assertEquals("00000000000000000000", source.readByteString().hex())
      }
    }
  }

  @Test fun fileHandleFlush() {
    if (!supportsFileHandle()) return
    if (windowsLimitations) return // Open for reading and writing simultaneously.

    val path = base / "file-handle-flush"
    fileSystem.openReadWrite(path).use { handleA ->
      handleA.sink().buffer().use { sink ->
        sink.writeUtf8("abcde")
      }
      handleA.flush()

      fileSystem.openReadWrite(path).use { handleB ->
        handleB.source().buffer().use { source ->
          assertEquals("abcde", source.readUtf8())
        }
      }
    }
  }

  @Test fun fileHandleLargeBufferedWriteAndRead() {
    if (!supportsFileHandle()) return
    if (isBrowser()) return // This test errors on browsers in CI.

    val data = randomBytes(1024 * 1024 * 8)

    val path = base / "file-handle-large-buffered-write-and-read"
    fileSystem.openReadWrite(path).use { handle ->
      handle.sink().buffer().use { sink ->
        sink.write(data)
      }
    }

    fileSystem.openReadWrite(path).use { handle ->
      handle.source().buffer().use { source ->
        assertEquals(data, source.readByteString())
      }
    }
  }

  @Test fun fileHandleLargeArrayWriteAndRead() {
    if (!supportsFileHandle()) return
    if (isBrowser()) return // This test errors on browsers in CI.

    val path = base / "file-handle-large-array-write-and-read"

    val writtenBytes = randomBytes(1024 * 1024 * 8)
    fileSystem.openReadWrite(path).use { handle ->
      handle.write(0, writtenBytes.toByteArray(), 0, writtenBytes.size)
    }

    val readBytes = fileSystem.openReadWrite(path).use { handle ->
      val byteArray = ByteArray(writtenBytes.size)
      handle.read(0, byteArray, 0, byteArray.size)
      return@use byteArray.toByteString(0, byteArray.size) // Parameters necessary for issue 910.
    }

    assertEquals(writtenBytes, readBytes)
  }

  @Test fun fileHandleSinkPosition() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-sink-position"

    fileSystem.openReadWrite(path).use { handle ->
      handle.sink().use { sink ->
        sink.write(Buffer().writeUtf8("abcde"), 5)
        assertEquals(5, handle.position(sink))
        sink.write(Buffer().writeUtf8("fghijklmno"), 10)
        assertEquals(15, handle.position(sink))
      }

      handle.sink(200).use { sink ->
        sink.write(Buffer().writeUtf8("abcde"), 5)
        assertEquals(205, handle.position(sink))
        sink.write(Buffer().writeUtf8("fghijklmno"), 10)
        assertEquals(215, handle.position(sink))
      }
    }
  }

  @Test fun fileHandleBufferedSinkPosition() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-buffered-sink-position"

    fileSystem.openReadWrite(path).use { handle ->
      handle.sink().buffer().use { sink ->
        sink.writeUtf8("abcde")
        assertEquals(5, handle.position(sink))
        sink.writeUtf8("fghijklmno")
        assertEquals(15, handle.position(sink))
      }

      handle.sink(200).buffer().use { sink ->
        sink.writeUtf8("abcde")
        assertEquals(205, handle.position(sink))
        sink.writeUtf8("fghijklmno")
        assertEquals(215, handle.position(sink))
      }
    }
  }

  @Test fun fileHandleSinkReposition() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-sink-reposition"

    fileSystem.openReadWrite(path).use { handle ->
      handle.sink().use { sink ->
        sink.write(Buffer().writeUtf8("abcdefghij"), 10)
        handle.reposition(sink, 5)
        assertEquals(5, handle.position(sink))
        sink.write(Buffer().writeUtf8("KLM"), 3)
        assertEquals(8, handle.position(sink))

        handle.reposition(sink, 200)
        sink.write(Buffer().writeUtf8("ABCDEFGHIJ"), 10)
        handle.reposition(sink, 205)
        assertEquals(205, handle.position(sink))
        sink.write(Buffer().writeUtf8("klm"), 3)
        assertEquals(208, handle.position(sink))
      }

      Buffer().also {
        handle.read(fileOffset = 0, sink = it, byteCount = 10)
        assertEquals("abcdeKLMij", it.readUtf8())
      }

      Buffer().also {
        handle.read(fileOffset = 200, sink = it, byteCount = 15)
        assertEquals("ABCDEklmIJ", it.readUtf8())
      }
    }
  }

  @Test fun fileHandleBufferedSinkReposition() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-buffered-sink-reposition"

    fileSystem.openReadWrite(path).use { handle ->
      handle.sink().buffer().use { sink ->
        sink.write(Buffer().writeUtf8("abcdefghij"), 10)
        handle.reposition(sink, 5)
        assertEquals(5, handle.position(sink))
        sink.write(Buffer().writeUtf8("KLM"), 3)
        assertEquals(8, handle.position(sink))

        handle.reposition(sink, 200)
        sink.write(Buffer().writeUtf8("ABCDEFGHIJ"), 10)
        handle.reposition(sink, 205)
        assertEquals(205, handle.position(sink))
        sink.write(Buffer().writeUtf8("klm"), 3)
        assertEquals(208, handle.position(sink))
      }

      Buffer().also {
        handle.read(fileOffset = 0, sink = it, byteCount = 10)
        assertEquals("abcdeKLMij", it.readUtf8())
      }

      Buffer().also {
        handle.read(fileOffset = 200, sink = it, byteCount = 15)
        assertEquals("ABCDEklmIJ", it.readUtf8())
      }
    }
  }

  @Test fun fileHandleSourceHappyPath() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }

    fileSystem.openReadOnly(path).use { handle ->
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

  @Test fun fileHandleSourceReposition() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-source-reposition"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }

    fileSystem.openReadOnly(path).use { handle ->
      assertEquals(16L, handle.size())
      val buffer = Buffer()

      handle.source().use { source ->
        handle.reposition(source, 12L)
        assertEquals(12L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals("mnop", buffer.readUtf8())
        assertEquals(-1L, source.read(buffer, 4L))
        assertEquals("", buffer.readUtf8())
        assertEquals(16L, handle.position(source))

        handle.reposition(source, 0L)
        assertEquals(0L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals("abcd", buffer.readUtf8())
        assertEquals(4L, handle.position(source))

        handle.reposition(source, 8L)
        assertEquals(8L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals("ijkl", buffer.readUtf8())
        assertEquals(12L, handle.position(source))

        handle.reposition(source, 16L)
        assertEquals(16L, handle.position(source))
        assertEquals(-1L, source.read(buffer, 4L))
        assertEquals("", buffer.readUtf8())
        assertEquals(16L, handle.position(source))
      }
    }
  }

  @Test fun fileHandleBufferedSourceReposition() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-buffered-source-reposition"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }

    fileSystem.openReadOnly(path).use { handle ->
      assertEquals(16L, handle.size())
      val buffer = Buffer()

      handle.source().buffer().use { source ->
        handle.reposition(source, 12L)
        assertEquals(0L, source.buffer.size)
        assertEquals(12L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals(0L, source.buffer.size)
        assertEquals("mnop", buffer.readUtf8())
        assertEquals(-1L, source.read(buffer, 4L))
        assertEquals("", buffer.readUtf8())
        assertEquals(16L, handle.position(source))

        handle.reposition(source, 0L)
        assertEquals(0L, source.buffer.size)
        assertEquals(0L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals(12L, source.buffer.size) // Buffered bytes accumulated.
        assertEquals("abcd", buffer.readUtf8())
        assertEquals(4L, handle.position(source))

        handle.reposition(source, 8L)
        assertEquals(8L, source.buffer.size) // Buffered bytes preserved.
        assertEquals(8L, handle.position(source))
        assertEquals(4L, source.read(buffer, 4L))
        assertEquals(4L, source.buffer.size)
        assertEquals("ijkl", buffer.readUtf8())
        assertEquals(12L, handle.position(source))

        handle.reposition(source, 16L)
        assertEquals(0L, source.buffer.size)
        assertEquals(16L, handle.position(source))
        assertEquals(-1L, source.read(buffer, 4L))
        assertEquals(0L, source.buffer.size)
        assertEquals("", buffer.readUtf8())
        assertEquals(16L, handle.position(source))
      }
    }
  }

  @Test fun fileHandleSourceSeekBackwards() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-source-backwards"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    fileSystem.openReadOnly(path).use { handle ->
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

  @Test fun bufferedFileHandleSourceHappyPath() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }

    fileSystem.openReadOnly(path).use { handle ->
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

  @Test fun bufferedFileHandleSourceSeekBackwards() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-source-backwards"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }
    fileSystem.openReadOnly(path).use { handle ->
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

  @Test fun openReadOnlyThrowsOnAttemptToWrite() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-source"
    fileSystem.write(path) {
      writeUtf8("abcdefghijklmnop")
    }

    fileSystem.openReadOnly(path).use { handle ->
      try {
        handle.sink()
        fail()
      } catch (_: IllegalStateException) {
      }

      try {
        handle.flush()
        fail()
      } catch (_: IllegalStateException) {
      }

      try {
        handle.resize(0L)
        fail()
      } catch (_: IllegalStateException) {
      }

      try {
        handle.write(0L, Buffer().writeUtf8("hello"), 5L)
        fail()
      } catch (_: IllegalStateException) {
      }
    }
  }

  @Test fun openReadOnlyFailsOnAbsentFile() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-source"

    try {
      fileSystem.openReadOnly(path)
      fail()
    } catch (_: IOException) {
    }
  }

  @Test fun openReadWriteCreatesAbsentFile() {
    if (!supportsFileHandle()) return

    val path = base / "file-handle-source"

    fileSystem.openReadWrite(path).use {
    }

    assertEquals("", path.readUtf8())
  }

  @Test fun sinkPositionFailsAfterClose() {
    if (!supportsFileHandle()) return

    val path = base / "sink-position-fails-after-close"

    fileSystem.openReadWrite(path).use { handle ->
      val sink = handle.sink()
      sink.close()
      try {
        handle.position(sink)
        fail()
      } catch (_: IllegalStateException) {
      }
      try {
        handle.position(sink.buffer())
        fail()
      } catch (_: IllegalStateException) {
      }
    }
  }

  @Test fun sinkRepositionFailsAfterClose() {
    if (!supportsFileHandle()) return

    val path = base / "sink-reposition-fails-after-close"

    fileSystem.openReadWrite(path).use { handle ->
      val sink = handle.sink()
      sink.close()
      try {
        handle.reposition(sink, 1L)
        fail()
      } catch (_: IllegalStateException) {
      }
      try {
        handle.reposition(sink.buffer(), 1L)
        fail()
      } catch (_: IllegalStateException) {
      }
    }
  }

  @Test fun sourcePositionFailsAfterClose() {
    if (!supportsFileHandle()) return

    val path = base / "source-position-fails-after-close"

    fileSystem.openReadWrite(path).use { handle ->
      val source = handle.source()
      source.close()
      try {
        handle.position(source)
        fail()
      } catch (_: IllegalStateException) {
      }
      try {
        handle.position(source.buffer())
        fail()
      } catch (_: IllegalStateException) {
      }
    }
  }

  @Test fun sourceRepositionFailsAfterClose() {
    if (!supportsFileHandle()) return

    val path = base / "source-reposition-fails-after-close"

    fileSystem.openReadWrite(path).use { handle ->
      val source = handle.source()
      source.close()
      try {
        handle.reposition(source, 1L)
        fail()
      } catch (_: IllegalStateException) {
      }
      try {
        handle.reposition(source.buffer(), 1L)
        fail()
      } catch (_: IllegalStateException) {
      }
    }
  }

  @Test fun sizeFailsAfterClose() {
    if (!supportsFileHandle()) return

    val path = base / "size-fails-after-close"

    val handle = fileSystem.openReadWrite(path)
    handle.close()
    try {
      handle.size()
      fail()
    } catch (_: IllegalStateException) {
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
      "FakeFileSystem",
      "JvmSystemFileSystem",
      "NioSystemFileSystem",
      "PosixFileSystem",
      "NodeJsFileSystem" -> true
      else -> false
    }
  }

  private fun expectIOExceptionOnWindows(exceptJs: Boolean = false, block: () -> Unit) {
    val expectCrash = windowsLimitations && (!isNodeJsFileSystem || !exceptJs)
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

  fun Path.createDirectory() {
    fileSystem.createDirectory(this)
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
    return Instant.fromEpochSeconds(plus(Duration.seconds(2)).epochSeconds)
  }

  private fun assertInRange(sampled: Instant?, minTime: Instant, maxTime: Instant) {
    if (sampled == null) return
    val minFsTime = minTime.minFileSystemTime()
    val maxFsTime = maxTime.maxFileSystemTime()
    assertTrue("expected $sampled in $minFsTime..$maxFsTime (relaxed from $minTime..$maxTime)") {
      sampled in minFsTime..maxFsTime
    }
  }
}
