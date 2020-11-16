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

/**
 * A fully in-memory filesystem useful for testing. It includes features to support writing
 * better tests.
 *
 * Use [openPaths] to see which paths have been opened for read or write, but not yet closed. Tests
 * should assert that this list is empty in `tearDown()`. This way the test only pass if all streams
 * that were opened are also closed.
 */
class FakeFilesystem : Filesystem() {
  private val root = "/".toPath()

  /** Keys are canonical paths. Each value is either a [Directory] or a [ByteString]. */
  private val elements = mutableMapOf<Path, Any>(root to Directory)

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

  override fun list(dir: Path): List<Path> {
    val canonicalPath = root / dir
    val element = elements[canonicalPath] ?: throw IOException("no such file")

    if (element !is Directory) {
      throw IOException("not a directory")
    }

    return elements.keys.filter { it.parent == canonicalPath }
  }

  override fun source(file: Path): Source {
    val canonicalPath = root / file
    val element = elements[canonicalPath] ?: throw IOException("no such file")

    if (element !is ByteString) {
      throw IOException("not a file")
    }

    openPathsMutable += canonicalPath
    return FakeFileSource(canonicalPath, Buffer().write(element))
  }

  override fun sink(file: Path): Sink {
    val canonicalPath = root / file

    if (elements[canonicalPath] is Directory) {
      throw IOException("destination is a directory")
    }
    if (elements[canonicalPath.parent] !is Directory) {
      throw IOException("parent isn't a directory")
    }

    openPathsMutable += canonicalPath
    elements[canonicalPath] = ByteString.EMPTY
    return FakeFileSink(canonicalPath)
  }

  override fun createDirectory(dir: Path) {
    val canonicalPath = root / dir

    if (elements[canonicalPath] != null) {
      throw IOException("already exists")
    }
    if (elements[canonicalPath.parent] !is Directory) {
      throw IOException("parent isn't a directory")
    }

    elements[canonicalPath] = Directory
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

  override fun temporaryDirectory(): Path {
    return root
  }

  internal object Directory

  /** Reads data from [buffer], removing itself from [openPathsMutable] when closed. */
  internal inner class FakeFileSource(val path: Path, val buffer: Buffer) : Source {
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
  internal inner class FakeFileSink(val path: Path) : Sink {
    private var buffer = Buffer()
    private var closed = false

    override fun write(source: Buffer, byteCount: Long) {
      check(!closed) { "closed" }
      buffer.write(source, byteCount)
    }

    override fun flush() {
      check(!closed) { "closed" }
      elements[path] = buffer.snapshot()
    }

    override fun timeout() = Timeout.NONE

    override fun close() {
      if (closed) return
      closed = true
      elements[path] = buffer.snapshot()
      openPathsMutable -= path
    }
  }
}
