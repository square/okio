/*
 * Copyright (C) 2023 Square, Inc.
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
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source
import okio.internal.preview1.fdClose
import okio.internal.preview1.fdReadDir
import okio.internal.preview1.oflag_creat
import okio.internal.preview1.oflag_directory
import okio.internal.preview1.oflag_excl
import okio.internal.preview1.pathCreateDirectory
import okio.internal.preview1.pathOpen
import okio.internal.preview1.right_fd_readdir
import okio.internal.preview1.right_fd_write

/**
 * Use [WASI] to implement the Okio file system interface.
 *
 * [WASI]: https://wasi.dev/
 */
object WasiFileSystem : FileSystem() {
  override fun canonicalize(path: Path): Path {
    TODO("Not yet implemented")
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    TODO("Not yet implemented")
  }

  override fun list(dir: Path): List<Path> {
    val fd = pathOpen(
      path = dir.toString(),
      oflags = oflag_directory,
      rightsBase = right_fd_readdir,
    )
    try {
      val results = fdReadDir(fd)
      return results.map { dir / it }
    } finally {
      fdClose(fd)
    }
  }

  override fun listOrNull(dir: Path): List<Path>? {
    // TODO: don't throw if the directory doesn't exist, etc.
    return list(dir)
  }

  override fun openReadOnly(file: Path): FileHandle {
    TODO("Not yet implemented")
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    TODO("Not yet implemented")
  }

  override fun source(file: Path): Source {
    TODO("Not yet implemented")
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    val oflags = when {
      mustCreate -> oflag_creat or oflag_excl
      else -> oflag_creat
    }

    return FileSink(
      fd = pathOpen(
        path = file.toString(),
        oflags = oflags,
        rightsBase = right_fd_write,
      )
    )
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    TODO("Not yet implemented")
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    pathCreateDirectory(path = dir.toString())
  }

  override fun atomicMove(source: Path, target: Path) {
    TODO("Not yet implemented")
  }

  override fun delete(path: Path, mustExist: Boolean) {
    TODO("Not yet implemented")
  }

  override fun createSymlink(source: Path, target: Path) {
    TODO("Not yet implemented")
  }

  override fun toString() = "WasiFileSystem"
}
