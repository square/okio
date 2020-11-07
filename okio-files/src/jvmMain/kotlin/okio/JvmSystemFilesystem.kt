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
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

object JvmSystemFilesystem : Filesystem() {
  override fun baseDirectory(): Path {
    val userDir = System.getProperty("user.dir")
      ?: throw IOException("user.dir system property missing?!")
    return userDir.toPath()
  }

  override fun list(dir: Path): List<Path> {
    val entries = dir.toFile().list() ?: throw IOException("failed to list $dir")
    return entries.map { dir / it }
  }

  override fun source(file: Path): Source {
    return file.toFile().source()
  }

  override fun sink(file: Path): Sink {
    return file.toFile().sink()
  }

  override fun createDirectory(dir: Path) {
    if (!dir.toFile().mkdir()) throw IOException("failed to create directory $dir")
  }

  override fun atomicMove(
    source: Path,
    target: Path
  ) {
    try {
      Files.move(source.toNioPath(), target.toNioPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    } catch (e: UnsupportedOperationException) {
      throw IOException("atomic move not supported")
    }
  }

  override fun copy(
    source: Path,
    target: Path
  ) {
    commonCopy(source, target)
  }

  override fun delete(path: Path) {
    val deleted = path.toFile().delete()
    if (!deleted) throw IOException("failed to delete $path")
  }
}
