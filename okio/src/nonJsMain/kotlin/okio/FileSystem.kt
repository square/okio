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

import okio.internal.commonCopy
import okio.internal.commonCreateDirectories
import okio.internal.commonDeleteRecursively
import okio.internal.commonExists
import okio.internal.commonMetadata
import kotlin.jvm.JvmField

@ExperimentalFileSystem
actual abstract class FileSystem {
  @Throws(IOException::class)
  actual abstract fun canonicalize(path: Path): Path

  @Throws(IOException::class)
  actual fun metadata(path: Path): FileMetadata = commonMetadata(path)

  @Throws(IOException::class)
  actual abstract fun metadataOrNull(path: Path): FileMetadata?

  @Throws(IOException::class)
  actual fun exists(path: Path): Boolean = commonExists(path)

  @Throws(IOException::class)
  actual abstract fun list(dir: Path): List<Path>

  @Throws(IOException::class)
  actual abstract fun open(file: Path): FileHandle

  @Throws(IOException::class)
  actual abstract fun source(file: Path): Source

  @Throws(IOException::class)
  actual inline fun <T> read(file: Path, readerAction: BufferedSource.() -> T): T {
    return source(file).buffer().use {
      it.readerAction()
    }
  }

  @Throws(IOException::class)
  actual abstract fun sink(file: Path): Sink

  @Throws(IOException::class)
  actual inline fun <T> write(file: Path, writerAction: BufferedSink.() -> T): T {
    return sink(file).buffer().use {
      it.writerAction()
    }
  }

  @Throws(IOException::class)
  actual abstract fun appendingSink(file: Path): Sink

  @Throws(IOException::class)
  actual abstract fun createDirectory(dir: Path)

  @Throws(IOException::class)
  actual fun createDirectories(dir: Path): Unit = commonCreateDirectories(dir)

  @Throws(IOException::class)
  actual abstract fun atomicMove(source: Path, target: Path)

  @Throws(IOException::class)
  actual open fun copy(source: Path, target: Path): Unit = commonCopy(source, target)

  @Throws(IOException::class)
  actual abstract fun delete(path: Path)

  @Throws(IOException::class)
  actual open fun deleteRecursively(fileOrDirectory: Path): Unit =
    commonDeleteRecursively(fileOrDirectory)

  actual companion object {
    /**
     * The current process's host file system. Use this instance directly, or dependency inject a
     * [FileSystem] to make code testable.
     */
    @JvmField
    val SYSTEM: FileSystem = PLATFORM_FILE_SYSTEM

    @JvmField
    actual val SYSTEM_TEMPORARY_DIRECTORY: Path = PLATFORM_TEMPORARY_DIRECTORY
  }
}
