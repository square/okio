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
package okio.internal

import okio.ExperimentalFileSystem
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Sink

@ExperimentalFileSystem
abstract class ReadOnlyFilesystem : FileSystem() {
  override fun sink(file: Path): Sink = throw IOException("$this is read-only")

  override fun appendingSink(file: Path): Sink =
    throw IOException("$this is read-only")

  override fun createDirectory(dir: Path): Unit =
    throw IOException("$this is read-only")

  override fun atomicMove(source: Path, target: Path): Unit =
    throw IOException("$this is read-only")

  override fun delete(path: Path): Unit = throw IOException("$this is read-only")
}
