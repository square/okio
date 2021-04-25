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

import kotlin.time.ExperimentalTime
import kotlinx.datetime.Clock

/**
 * Run a regular file system test, but use [FileHandle] for more file system operations than usual.
 * This is intended to increase test coverage for [FileHandle].
 */
@ExperimentalTime
@ExperimentalFileSystem
class FileHandleFileSystemTest : AbstractFileSystemTest(
  clock = Clock.System,
  fileSystem = FileHandleTestingFileSystem(FileSystem.SYSTEM),
  windowsLimitations = Path.DIRECTORY_SEPARATOR == "\\",
  allowClobberingEmptyDirectories = Path.DIRECTORY_SEPARATOR == "\\",
  temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
) {
  /**
   * A testing-only file system that implements all reading and writing operations with
   * [FileHandle]. This is intended to increase test coverage for [FileHandle].
   */
  class FileHandleTestingFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
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
  }
}

