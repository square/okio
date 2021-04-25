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
package okio.fakefilesystem

import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.ArrayIndexOutOfBoundsException
import okio.Buffer
import okio.ByteString
import okio.ExperimentalFileSystem
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.fakefilesystem.FakeFileSystem.Element.Directory
import okio.fakefilesystem.FakeFileSystem.Element.File
import okio.fakefilesystem.FakeFileSystem.Operation.READ
import okio.fakefilesystem.FakeFileSystem.Operation.WRITE

/**
 * A fully in-memory file system useful for testing. It includes features to support writing
 * better tests.
 *
 * Use [openPaths] to see which paths have been opened for read or write, but not yet closed. Tests
 * should call [checkNoOpenFiles] in `tearDown()` to confirm that no file streams were leaked.
 *
 * Strict By Default
 * -----------------
 *
 * By default this file system is strict. These actions are not allowed and throw an [IOException]
 * if attempted:
 *
 *  * Moving a file that is currently open for reading or writing.
 *  * Deleting a file that is currently open for reading or writing.
 *  * Moving a file to a path that currently resolves to an empty directory.
 *  * Reading and writing the same file at the same time.
 *  * Opening a file for writing that is already open for writing.
 *
 * Programs that do not attempt any of the above operations should work fine on both UNIX and
 * Windows systems. Relax these constraints individually or call [emulateWindows] or [emulateUnix];
 * to apply the constraints of a particular operating system.
 */
@ExperimentalFileSystem
class FakeFileSystem(
  @JvmField
  val clock: Clock = Clock.System
) : FileSystem() {

  /** Keys are canonical paths. Each value is either a [Directory] or a [ByteString]. */
  private val elements = mutableMapOf<Path, Element>()

  /** Files that are currently open and need to be closed to avoid resource leaks. */
  private val openFiles = mutableListOf<OpenFile>()

  /**
   * An absolute path with this file system's current working directory. Relative paths will be
   * resolved against this directory when they are used.
   */
  var workingDirectory: Path = "/".toPath()
    set(value) {
      require(value.isAbsolute) {
        "expected an absolute path but was $value"
      }
      field = value
    }

  /**
   * True to allow files to be moved even if they're currently open for read or write. UNIX file
   * systems typically allow open files to be moved; Windows file systems do not.
   */
  var allowMovingOpenFiles = false

  /**
   * True to allow files to be deleted even if they're currently open for read or write. UNIX file
   * systems typically allow open files to be deleted; Windows file systems do not.
   */
  var allowDeletingOpenFiles = false

  /**
   * True to allow the target of an [atomicMove] operation to be an empty directory. Windows file
   * systems typically allow files to replace empty directories; UNIX file systems do not.
   */
  var allowClobberingEmptyDirectories = false

  /**
   * True to permit a file to have multiple [sinks][sink] open at the same time. Both Windows and
   * UNIX file systems permit this but the result may be undefined.
   */
  var allowWritesWhileWriting = false

  /**
   * True to permit a file to have a [source] and [sink] open at the same time. Both Windows and
   * UNIX file systems permit this but the result may be undefined.
   */
  var allowReadsWhileWriting = false

  /**
   * Canonical paths for every file and directory in this file system. This omits file system roots
   * like `C:\` and `/`.
   */
  @get:JvmName("allPaths")
  val allPaths: Set<Path>
    get() {
      val result = mutableListOf<Path>()
      for (path in elements.keys) {
        if (path.isRoot) continue
        result += path
      }
      result.sort()
      return result.toSet()
    }

  /**
   * Canonical paths currently opened for reading or writing in the order they were opened. This may
   * contain duplicates if a single path is open by multiple readers.
   *
   * Note that this may contain paths not present in [allPaths]. This occurs if a file is deleted
   * while it is still open.
   *
   * The returned list is ordered by the order that the paths were opened.
   */
  @get:JvmName("openPaths")
  val openPaths: List<Path>
    get() = openFiles.map { it.canonicalPath }

  /**
   * Confirm that all files that have been opened on this file system (with [source], [sink], and
   * [appendingSink]) have since been closed. Call this in your test's `tearDown()` function to
   * confirm that your program hasn't leaked any open files.
   *
   * Forgetting to close a file on a real file system is a severe error that may lead to a program
   * crash. The operating system enforces a limit on how many files may be open simultaneously. On
   * Linux this is [getrlimit] and is commonly adjusted with the `ulimit` command.
   *
   * [getrlimit]: https://man7.org/linux/man-pages/man2/getrlimit.2.html
   *
   * @throws IllegalStateException if any files are open when this function is called.
   */
  fun checkNoOpenFiles() {
    val firstOpenFile = openFiles.firstOrNull() ?: return
    throw IllegalStateException(
      """
      |expected 0 open files, but found:
      |    ${openFiles.joinToString(separator = "\n    ") { it.canonicalPath.toString() }}
      """.trimMargin(),
      firstOpenFile.backtrace
    )
  }

  /**
   * Configure this file system to use a Windows-like working directory (`F:\`, unless the working
   * directory is already Windows-like) and to follow a Windows-like policy on what operations
   * are permitted.
   */
  fun emulateWindows() {
    if ("\\" !in workingDirectory.toString()) {
      workingDirectory = "F:\\".toPath()
    }
    allowMovingOpenFiles = false
    allowDeletingOpenFiles = false
    allowClobberingEmptyDirectories = true
    allowWritesWhileWriting = true
    allowReadsWhileWriting = true
  }

  /**
   * Configure this file system to use a UNIX-like working directory (`/`, unless the working
   * directory is already UNIX-like) and to follow a UNIX-like policy on what operations are
   * permitted.
   */
  fun emulateUnix() {
    if ("/" !in workingDirectory.toString()) {
      workingDirectory = "/".toPath()
    }
    allowMovingOpenFiles = true
    allowDeletingOpenFiles = true
    allowClobberingEmptyDirectories = false
    allowWritesWhileWriting = true
    allowReadsWhileWriting = true
  }

  override fun canonicalize(path: Path): Path {
    val canonicalPath = workingDirectory / path

    if (canonicalPath !in elements) {
      throw FileNotFoundException("no such file: $path")
    }

    return canonicalPath
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val canonicalPath = workingDirectory / path
    var element = elements[canonicalPath]

    // If the path is a root, create it on demand.
    if (element == null && canonicalPath.isRoot) {
      element = Directory(createdAt = clock.now())
      elements[canonicalPath] = element
    }

    return element?.metadata
  }

  override fun list(dir: Path): List<Path> {
    val canonicalPath = workingDirectory / dir
    val element = requireDirectory(canonicalPath)

    element.access(now = clock.now())
    val paths = elements.keys.filterTo(mutableListOf()) { it.parent == canonicalPath }
    if (dir.isRelative) {
      for (i in paths.indices) {
        paths[i] = dir / paths[i].name
      }
    }
    paths.sort()
    return paths
  }

  override fun source(file: Path): Source {
    val fileHandle = open(file, read = true)
    return fileHandle.source()
      .also { fileHandle.close() }
  }

  override fun sink(file: Path): Sink {
    val fileHandle = open(file, write = true)
    fileHandle.resize(0L) // If the file already has data, get rid of it.
    return fileHandle.sink()
      .also { fileHandle.close() }
  }

  override fun appendingSink(file: Path): Sink {
    val fileHandle = open(file, write = true)
    return fileHandle.appendingSink()
      .also { fileHandle.close() }
  }

  override fun open(
    file: Path,
    read: Boolean,
    write: Boolean
  ): FileHandle {
    val canonicalPath = workingDirectory / file
    val existing = elements[canonicalPath]
    val now = clock.now()
    val element: File
    val operation: Operation

    if (write) {
      // Note that this case is used for both write and read/write.
      if (existing is Directory) {
        throw IOException("destination is a directory: $file")
      }
      if (!allowWritesWhileWriting) {
        findOpenFile(canonicalPath, operation = WRITE)?.let {
          throw IOException("file is already open for writing $file", it.backtrace)
        }
      }
      if (!allowReadsWhileWriting) {
        findOpenFile(canonicalPath, operation = READ)?.let {
          throw IOException("file is already open for reading $file", it.backtrace)
        }
      }

      val parent = requireDirectory(canonicalPath.parent)
      parent.access(now, true)

      element = File(createdAt = existing?.createdAt ?: now)
      elements[canonicalPath] = element
      operation = WRITE

      if (existing is File) {
        element.data = existing.data
      }

    } else if (read) {
      if (existing == null) throw FileNotFoundException("no such file: $file")
      element = existing as? File ?: throw IOException("not a file: $file")
      operation = READ

      findOpenFile(canonicalPath, operation = WRITE)?.let {
        throw IOException("file is already open for writing $file", it.backtrace)
      }

    } else {
      throw IllegalArgumentException("unexpected open options read=$read write=$write")
    }

    element.access(now = clock.now(), modified = write)

    val openFile = OpenFile(canonicalPath, operation, Exception("file opened for $operation here"))
    openFiles += openFile

    return FakeFileHandle(read, write, openFile, element)
  }

  override fun createDirectory(dir: Path) {
    val canonicalPath = workingDirectory / dir

    if (elements[canonicalPath] != null) {
      throw IOException("already exists: $dir")
    }
    requireDirectory(canonicalPath.parent)

    elements[canonicalPath] = Directory(createdAt = clock.now())
  }

  override fun atomicMove(source: Path, target: Path) {
    val canonicalSource = workingDirectory / source
    val canonicalTarget = workingDirectory / target

    val targetElement = elements[canonicalTarget]
    val sourceElement = elements[canonicalSource]

    // Universal constraints.
    if (targetElement is Directory) {
      throw IOException("target is a directory: $target")
    }
    requireDirectory(canonicalTarget.parent)
    if (!allowMovingOpenFiles) {
      findOpenFile(canonicalSource)?.let {
        throw IOException("source is open $source", it.backtrace)
      }
      findOpenFile(canonicalTarget)?.let {
        throw IOException("target is open $target", it.backtrace)
      }
    }
    if (!allowClobberingEmptyDirectories) {
      if (sourceElement is Directory && targetElement is File) {
        throw IOException("source is a directory and target is a file")
      }
    }

    val removed = elements.remove(canonicalSource)
      ?: throw FileNotFoundException("source doesn't exist: $source")
    elements[canonicalTarget] = removed
  }

  override fun delete(path: Path) {
    val canonicalPath = workingDirectory / path

    if (elements.keys.any { it.parent == canonicalPath }) {
      throw IOException("non-empty directory")
    }

    if (!allowDeletingOpenFiles) {
      findOpenFile(canonicalPath)?.let {
        throw IOException("file is open $path", it.backtrace)
      }
    }

    if (elements.remove(canonicalPath) == null) {
      throw FileNotFoundException("no such file: $path")
    }
  }

  /**
   * Gets the directory at [path], creating it if [path] is a file system root.
   *
   * @throws IOException if the named directory is not a root and does not exist, or if it does
   *     exist but is not a directory.
   */
  private fun requireDirectory(path: Path?): Directory {
    if (path == null) throw IOException("directory does not exist")

    // If the path is a directory, return it!
    val element = elements[path]
    if (element is Directory) return element

    // If the path is a root, create a directory for it on demand.
    if (path.isRoot) {
      val root = Directory(createdAt = clock.now())
      elements[path] = root
      return root
    }

    if (element == null) throw FileNotFoundException("no such directory: $path")

    throw IOException("not a directory: $path")
  }

  private sealed class Element(
    val createdAt: Instant
  ) {
    var lastModifiedAt: Instant = createdAt
    var lastAccessedAt: Instant = createdAt

    class File(createdAt: Instant) : Element(createdAt) {
      var data: ByteString = ByteString.EMPTY

      override val metadata: FileMetadata
        get() = FileMetadata(
          isRegularFile = true,
          size = data.size.toLong(),
          createdAt = createdAt,
          lastModifiedAt = lastModifiedAt,
          lastAccessedAt = lastAccessedAt
        )
    }

    class Directory(createdAt: Instant) : Element(createdAt) {
      override val metadata: FileMetadata
        get() = FileMetadata(
          isDirectory = true,
          createdAt = createdAt,
          lastModifiedAt = lastModifiedAt,
          lastAccessedAt = lastAccessedAt
        )
    }

    fun access(now: Instant, modified: Boolean = false) {
      lastAccessedAt = now
      if (modified) {
        lastModifiedAt = now
      }
    }

    abstract val metadata: FileMetadata
  }

  private fun findOpenFile(canonicalPath: Path, operation: Operation? = null): OpenFile? {
    return openFiles.firstOrNull {
      it.canonicalPath == canonicalPath && (operation == null || operation == it.operation)
    }
  }

  private fun checkOffsetAndCount(size: Long, offset: Long, byteCount: Long) {
    if (offset or byteCount < 0 || offset > size || size - offset < byteCount) {
      throw ArrayIndexOutOfBoundsException("size=$size offset=$offset byteCount=$byteCount")
    }
  }

  private class OpenFile(
    val canonicalPath: Path,
    val operation: Operation,
    val backtrace: Throwable
  )

  private enum class Operation {
    READ,
    WRITE
  }

  private inner class FakeFileHandle(
    private val readAccess: Boolean,
    private val writeAccess: Boolean,
    private val openFile: OpenFile,
    private val file: File
  ) : FileHandle() {
    private var closed = false

    override fun resize(size: Long) {
      check(!closed) { "closed" }

      val delta = size - file.data.size
      if (delta > 0) {
        file.data = Buffer()
          .write(file.data)
          .write(ByteArray(delta.toInt()))
          .readByteString()
      } else {
        file.data = file.data.substring(0, size.toInt())
      }

      file.access(now = clock.now(), modified = true)
    }

    override fun size(): Long {
      check(!closed) { "closed" }
      return file.data.size.toLong()
    }

    override fun protectedRead(
      fileOffset: Long,
      array: ByteArray,
      arrayOffset: Int,
      byteCount: Int
    ): Int {
      check(!closed) { "closed" }
      check(readAccess) { "not opened for read" }
      checkOffsetAndCount(array.size.toLong(), arrayOffset.toLong(), byteCount.toLong())

      val fileOffsetInt = fileOffset.toInt()
      val toCopy = minOf(file.data.size - fileOffsetInt, byteCount)
      if (toCopy <= 0) return -1
      for (i in 0 until toCopy) {
        array[i + arrayOffset] = file.data[i + fileOffsetInt]
      }
      return toCopy
    }

    override fun protectedWrite(
      fileOffset: Long,
      array: ByteArray,
      arrayOffset: Int,
      byteCount: Int
    ) {
      check(!closed) { "closed" }
      check(writeAccess) { "not opened for write" }
      checkOffsetAndCount(array.size.toLong(), arrayOffset.toLong(), byteCount.toLong())

      val buffer = Buffer()
      buffer.write(file.data, 0, minOf(fileOffset.toInt(), file.data.size))
      while (buffer.size < fileOffset) {
        buffer.writeByte(0)
      }
      buffer.write(array, arrayOffset, byteCount)
      if (buffer.size < file.data.size) {
        buffer.write(file.data, buffer.size.toInt(), file.data.size - buffer.size.toInt())
      }
      file.data = buffer.snapshot()
      file.access(now = clock.now(), modified = true)
    }

    override fun protectedFlush() {
      check(!closed) { "closed" }
      check(writeAccess) { "not opened for write" }
    }

    override fun protectedClose() {
      if (closed) return
      closed = true
      file.access(now = clock.now(), modified = writeAccess)
      openFiles -= openFile
    }

    override fun toString() = "FileHandler(${openFile.canonicalPath})"
  }

  override fun toString() = "FakeFileSystem"
}
