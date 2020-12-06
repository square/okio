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
 */
class FakeFilesystem(
  val clock: Clock = Clock.System
) : Filesystem() {
  private val root = "/".toPath()

  /** Keys are canonical paths. Each value is either a [Directory] or a [ByteString]. */
  private val elements = mutableMapOf<Path, Element>(root to Directory(clock.now()))

  private val openPathsMutable = mutableListOf<Path>()

  /**
   * Canonical paths currently opened for reading or writing in the order they were opened. This may
   * contain duplicates if a single path is open by multiple readers.
   */
  val openPaths: List<Path>
    get() = openPathsMutable.toList()

  override fun canonicalize(path: Path): Path {
    val canonicalPath = root / path

    if (canonicalPath !in elements) {
      throw IOException("no such file")
    }

    return canonicalPath
  }

  override fun metadata(path: Path): FileMetadata {
    val canonicalPath = root / path
    val element = elements[canonicalPath] ?: throw IOException("no such file")
    return element.metadata
  }

  override fun list(dir: Path): List<Path> {
    val canonicalPath = root / dir
    val element = elements[canonicalPath] ?: throw IOException("no such file")

    if (element !is Directory) {
      throw IOException("not a directory")
    }

    element.access(now = clock.now())
    return elements.keys.filter { it.parent == canonicalPath }
  }

  override fun source(file: Path): Source {
    val canonicalPath = root / file
    val element = elements[canonicalPath] ?: throw IOException("no such file")

    if (element !is File) {
      throw IOException("not a file")
    }

    openPathsMutable += canonicalPath
    element.access(now = clock.now())
    return FakeFileSource(canonicalPath, Buffer().write(element.data))
  }

  override fun sink(file: Path): Sink {
    val canonicalPath = root / file
    val now = clock.now()

    val existing = elements[canonicalPath]
    if (existing is Directory) {
      throw IOException("destination is a directory")
    }

    val parent = elements[canonicalPath.parent]
    if (parent !is Directory) {
      throw IOException("parent isn't a directory")
    }
    parent.access(now, true)

    openPathsMutable += canonicalPath
    val regularFile = File(createdAt = existing?.createdAt ?: now)
    regularFile.access(now = now, modified = true)
    elements[canonicalPath] = regularFile
    return FakeFileSink(canonicalPath, regularFile)
  }

  override fun createDirectory(dir: Path) {
    val canonicalPath = root / dir

    if (elements[canonicalPath] != null) {
      throw IOException("already exists")
    }
    if (elements[canonicalPath.parent] !is Directory) {
      throw IOException("parent isn't a directory")
    }

    elements[canonicalPath] = Directory(createdAt = clock.now())
  }

  override fun atomicMove(source: Path, target: Path) {
    val canonicalSource = root / source
    val canonicalTarget = root / target

    if (elements[canonicalTarget] is Directory) {
      throw IOException("target is a directory")
    }
    if (elements[canonicalTarget.parent] !is Directory) {
      throw IOException("target parent isn't a directory")
    }

    val removed = elements.remove(canonicalSource) ?: throw IOException("source doesn't exist")
    elements[canonicalTarget] = removed
  }

  override fun copy(source: Path, target: Path) {
    commonCopy(source, target)
  }

  override fun delete(path: Path) {
    val canonicalPath = root / path

    if (elements.keys.any { it.parent == canonicalPath }) {
      throw IOException("non-empty directory")
    }

    if (elements.remove(canonicalPath) == null) throw IOException("no such file")
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
    private var buffer = Buffer()
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
}
