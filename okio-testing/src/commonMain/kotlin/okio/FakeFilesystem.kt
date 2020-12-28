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
import okio.FakeFilesystem.Element.Directory
import okio.FakeFilesystem.Element.File
import okio.Path.Companion.toPath

/**
 * A fully in-memory filesystem useful for testing. It includes features to support writing
 * better tests.
 *
 * Use [openPaths] to see which paths have been opened for read or write, but not yet closed. Tests
 * should assert that this list is empty in `tearDown()`. This way the test only pass if all streams
 * that were opened are also closed.
 *
 * By default this filesystem permits deletion and removal of open files. Configure
 * [windowsLimitations] to true to throw an [IOException] when asked to delete or rename an open
 * file.
 */
@ExperimentalFilesystem
class FakeFilesystem(
  val clock: Clock = Clock.System,
  private val windowsLimitations: Boolean = false,
  private val workingDirectory: Path = (if (windowsLimitations) "F:\\".toPath() else "/".toPath())
) : Filesystem() {

  init {
    require(workingDirectory.isAbsolute) {
      "expected an absolute path but was $workingDirectory"
    }
  }

  /** Keys are canonical paths. Each value is either a [Directory] or a [ByteString]. */
  private val elements = mutableMapOf<Path, Element>()

  private val openPathsMutable = mutableListOf<Path>()

  /**
   * Canonical paths for every file and directory in this filesystem. This omits filesystem roots
   * like `C:\` and `/`.
   */
  val allPaths: Set<Path>
    get() {
      val result = mutableSetOf<Path>()
      for (path in elements.keys) {
        if (path.isRoot) continue
        result += path
      }
      return result
    }

  /**
   * Canonical paths currently opened for reading or writing in the order they were opened. This may
   * contain duplicates if a single path is open by multiple readers.
   *
   * Note that this may contain paths not present in [allPaths]. This occurs if a file is deleted
   * while it is still open.
   */
  val openPaths: List<Path>
    get() = openPathsMutable.toList()

  override fun canonicalize(path: Path): Path {
    val canonicalPath = workingDirectory / path

    if (canonicalPath !in elements) {
      throw IOException("no such file: $path")
    }

    return canonicalPath
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val canonicalPath = workingDirectory / path
    var element = elements[canonicalPath]

    // If the path is a root, create it on demand.
    if (element == null && path.isRoot) {
      element = Directory(createdAt = clock.now())
      elements[path] = element
    }

    return element?.metadata
  }

  override fun list(dir: Path): List<Path> {
    val canonicalPath = workingDirectory / dir
    val element = requireDirectory(canonicalPath)

    element.access(now = clock.now())
    return elements.keys.filter { it.parent == canonicalPath }
  }

  override fun source(file: Path): Source {
    val canonicalPath = workingDirectory / file
    val element = elements[canonicalPath] ?: throw IOException("no such file: $file")

    if (element !is File) {
      throw IOException("not a file: $file")
    }

    openPathsMutable += canonicalPath
    element.access(now = clock.now())
    return FakeFileSource(canonicalPath, Buffer().write(element.data))
  }

  override fun sink(file: Path): Sink {
    return newSink(file, append = false)
  }

  override fun appendingSink(file: Path): Sink {
    return newSink(file, append = true)
  }

  private fun newSink(file: Path, append: Boolean): Sink {
    val canonicalPath = workingDirectory / file
    val now = clock.now()

    val existing = elements[canonicalPath]
    if (existing is Directory) {
      throw IOException("destination is a directory: $file")
    }
    val parent = requireDirectory(canonicalPath.parent)
    parent.access(now, true)

    openPathsMutable += canonicalPath
    val regularFile = File(createdAt = existing?.createdAt ?: now)
    val result = FakeFileSink(canonicalPath, regularFile)
    if (append && existing is File) {
      result.buffer.write(existing.data)
      regularFile.data = existing.data
    }
    elements[canonicalPath] = regularFile
    regularFile.access(now = now, modified = true)
    return result
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
    if (windowsLimitations) {
      // Windows-only constraints.
      if (canonicalSource in openPathsMutable) {
        throw IOException("source is open $source")
      }
      if (canonicalTarget in openPathsMutable) {
        throw IOException("target is open $target")
      }
    } else {
      // UNIX-only constraints.
      if (sourceElement is Directory && targetElement is File) {
        throw IOException("source is a directory and target is a file")
      }
    }

    val removed = elements.remove(canonicalSource)
      ?: throw IOException("source doesn't exist: $source")
    elements[canonicalTarget] = removed
  }

  override fun delete(path: Path) {
    val canonicalPath = workingDirectory / path

    if (elements.keys.any { it.parent == canonicalPath }) {
      throw IOException("non-empty directory")
    }

    if (windowsLimitations && path in openPathsMutable) {
      throw IOException("file is open $path")
    }

    if (elements.remove(canonicalPath) == null) {
      throw IOException("no such file: $path")
    }
  }

  /**
   * Gets the directory at [path], creating it if [path] is a filesystem root.
   *
   * @throws IOException if the named directory is not a root and does not exist, or if it does
   *     exist but is not a directory.
   */
  private fun requireDirectory(path: Path?): Directory {
    if (path == null) throw IOException("directory does not exist")

    // If the path is a directory, return it!
    val element = elements[path]
    if (element is Directory) return element

    // If the path is a root, create it on demand.
    if (element == null && path.isRoot) {
      val root = Directory(createdAt = clock.now())
      elements[path] = root
      return root
    }

    throw IOException("path is not a directory: $path")
  }

  internal sealed class Element(
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

  /** Reads data from [buffer], removing itself from [openPathsMutable] when closed. */
  internal inner class FakeFileSource(
    private val path: Path,
    private val buffer: Buffer
  ) : Source {
    private var closed = false

    override fun read(sink: Buffer, byteCount: Long): Long {
      check(!closed) { "closed" }
      return buffer.read(sink, byteCount)
    }

    override fun timeout() = Timeout.NONE

    override fun close() {
      if (closed) return
      closed = true
      openPathsMutable -= path
    }
  }

  /** Writes data to [path]. */
  internal inner class FakeFileSink(
    private val path: Path,
    private val file: File
  ) : Sink {
    val buffer = Buffer()
    private var closed = false

    override fun write(source: Buffer, byteCount: Long) {
      check(!closed) { "closed" }
      buffer.write(source, byteCount)
    }

    override fun flush() {
      check(!closed) { "closed" }
      file.data = buffer.snapshot()
      file.access(now = clock.now(), modified = true)
    }

    override fun timeout() = Timeout.NONE

    override fun close() {
      if (closed) return
      closed = true
      file.data = buffer.snapshot()
      file.access(now = clock.now(), modified = true)
      openPathsMutable -= path
    }
  }

  override fun toString() = "FakeFilesystem"
}
