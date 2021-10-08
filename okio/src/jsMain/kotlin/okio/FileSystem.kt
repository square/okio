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
import okio.internal.commonCopy
import okio.internal.commonCreateDirectories
import okio.internal.commonDeleteRecursively
import okio.internal.commonExists
import okio.internal.commonListRecursively
import okio.internal.commonMetadata

@ExperimentalFileSystem
actual abstract class FileSystem {
  actual abstract fun canonicalize(path: Path): Path

  actual fun metadata(path: Path): FileMetadata = commonMetadata(path)

  actual abstract fun metadataOrNull(path: Path): FileMetadata?

  actual fun exists(path: Path): Boolean = commonExists(path)

  actual abstract fun list(dir: Path): List<Path>

  actual open fun listRecursively(dir: Path): Sequence<Path> = commonListRecursively(dir)

  actual abstract fun openReadOnly(file: Path): FileHandle

  actual abstract fun openReadWrite(file: Path): FileHandle

  actual abstract fun source(file: Path): Source

  actual inline fun <T> read(file: Path, readerAction: BufferedSource.() -> T): T {
    return source(file).buffer().use {
      it.readerAction()
    }
  }

  actual abstract fun sink(file: Path): Sink

  actual inline fun <T> write(file: Path, writerAction: BufferedSink.() -> T): T {
    return sink(file).buffer().use {
      it.writerAction()
    }
  }

  actual abstract fun appendingSink(file: Path): Sink

  actual abstract fun createDirectory(dir: Path)

  actual fun createDirectories(dir: Path): Unit = commonCreateDirectories(dir)

  actual abstract fun atomicMove(source: Path, target: Path)

  actual open fun copy(source: Path, target: Path): Unit = commonCopy(source, target)

  actual abstract fun delete(path: Path)

  actual open fun deleteRecursively(fileOrDirectory: Path): Unit =
    commonDeleteRecursively(fileOrDirectory)

  actual companion object {
    actual val SYSTEM_TEMPORARY_DIRECTORY: Path = tmpdir.toPath()
  }
}
