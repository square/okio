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
package okio

import java.io.InterruptedIOException
import java.nio.channels.FileChannel
import java.nio.file.FileSystem as JavaNioFileSystem
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path as NioPath
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.readSymbolicLink
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath

/**
 * A file system that wraps a `java.nio.file.FileSystem` and executes all operations in the context of the wrapped file
 * system.
 */
internal class NioFileSystemWrappingFileSystem(javaNioFileSystem: JavaNioFileSystem) : NioSystemFileSystem() {
  // TODO(Benoit) How do deal with multiple directories? Test with Windows someday.
  private val delegateRoot = javaNioFileSystem.rootDirectories.first()

  /**
   * On a `java.nio.file.FileSystem`, `java.nio.file.Path` are stateful and hold a reference to the file system they
   * got provided from. We need to [resolve][java.nio.file.Path.resolve] all okio paths before doing operations on the
   * nio file system in order for things to work properly.
   */
  private fun Path.resolve(readSymlink: Boolean = false): NioPath {
    val resolved = delegateRoot.resolve(toString())
    return if (readSymlink && resolved.isSymbolicLink()) {
      resolved.readSymbolicLink()
    } else {
      resolved
    }
  }

  override fun canonicalize(path: Path): Path {
    if (path.isAbsolute) {
      try {
        return path.resolve(readSymlink = true).toRealPath().toOkioPath()
      } catch (e: NoSuchFileException) {
        throw FileNotFoundException("no such file $path")
      }

    } else {
      return super.canonicalize(path)
    }
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    return metadataOrNull(path.resolve())
  }

  override fun list(dir: Path): List<Path> = list(dir, throwOnFailure = true)!!

  override fun listOrNull(dir: Path): List<Path>? = list(dir, throwOnFailure = false)

  private fun list(dir: Path, throwOnFailure: Boolean): List<Path>? {
    val nioDir = dir.resolve()
    val entries = try {
      nioDir.listDirectoryEntries()
    } catch (e: Exception) {
      if (throwOnFailure) {
        if (!nioDir.exists()) throw FileNotFoundException("no such file: $dir")
        throw IOException("failed to list $dir")
      } else {
        return null
      }
    }
    val result = entries.mapTo(mutableListOf()) { entry ->
      // TODO(Benoit) This whole block can surely be improved.
      val path = dir / entry.toOkioPath()
      if (dir.isRelative) {
        // All `entries` are absolute and resolving them against `dir` won't have any effect. We need to manually
        // relativize them back.
        nioDir.relativize(path.resolve()).toOkioPath()
      } else {
        path
      }
    }
    result.sort()
    return result
  }

  override fun openReadOnly(file: Path): FileHandle {
    val channel = try {
      FileChannel.open(file.resolve(), StandardOpenOption.READ)
    } catch (e: NoSuchFileException) {
      throw FileNotFoundException("no such file: $file")
    }
    return NioFileSystemFileHandle(readWrite = false, fileChannel = channel)
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    require(!mustCreate || !mustExist) { "Cannot require mustCreate and mustExist at the same time." }
    val openOptions = buildList {
      add(StandardOpenOption.READ)
      add(StandardOpenOption.WRITE)
      if (mustCreate) {
        add(StandardOpenOption.CREATE_NEW)
      } else if (!mustExist) {
        add(StandardOpenOption.CREATE)
      }
    }

    val channel = try {
      FileChannel.open(file.resolve(), *openOptions.toTypedArray())
    } catch (e: NoSuchFileException) {
      throw FileNotFoundException("no such file: $file")
    }
    return NioFileSystemFileHandle(readWrite = true, fileChannel = channel)
  }

  override fun source(file: Path): Source {
    try {
      return file.resolve(readSymlink = true).inputStream().source()
    } catch (e: NoSuchFileException) {
      throw FileNotFoundException("no such file: $file")
    }
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    val openOptions = buildList {
      if (mustCreate) add(StandardOpenOption.CREATE_NEW)
    }
    try {
      return file.resolve(readSymlink = true)
        .outputStream(*openOptions.toTypedArray())
        .sink()
    } catch (e: NoSuchFileException) {
      throw FileNotFoundException("no such file: $file")
    }
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    val openOptions = buildList {
      add(StandardOpenOption.APPEND)
      if (!mustExist) add(StandardOpenOption.CREATE)
    }
    return file.resolve()
      .outputStream(*openOptions.toTypedArray())
      .sink()
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    val alreadyExist = metadataOrNull(dir)?.isDirectory == true
    if (alreadyExist && mustCreate) {
      throw IOException("$dir already exist.")
    }

    try {
      dir.resolve().createDirectory()
    } catch (e: IOException) {
      if (alreadyExist) return
      throw IOException("failed to create directory: $dir", e)
    }
  }

  // Note that `java.nio.file.FileSystem` allows atomic moves of a file even if the target is an existing directory.
  override fun atomicMove(source: Path, target: Path) {
    try {
      Files.move(
        source.resolve(),
        target.resolve(),
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
      )
    } catch (e: NoSuchFileException) {
      throw FileNotFoundException(e.message)
    } catch (e: UnsupportedOperationException) {
      throw IOException("atomic move not supported")
    }
  }

  override fun delete(path: Path, mustExist: Boolean) {
    if (Thread.interrupted()) {
      // If the current thread has been interrupted.
      throw InterruptedIOException("interrupted")
    }
    val nioPath = path.resolve()
    try {
      Files.delete(nioPath)
    } catch (e: NoSuchFileException) {
      if (mustExist) throw FileNotFoundException("no such file: $path")
    } catch (e: IOException) {
      if (nioPath.exists()) throw IOException("failed to delete $path")
    }
  }

  override fun createSymlink(source: Path, target: Path) {
    val sourceNioPath = source.resolve()
    val targetNioPath =
      if (source.isAbsolute && target.isRelative) {
        sourceNioPath.parent.resolve(target.toString())
      } else {
        target.resolve()
      }
    Files.createSymbolicLink(sourceNioPath, targetNioPath)
  }

  override fun toString(): String = "NioFileSystemWrappingFileSystem"
}
