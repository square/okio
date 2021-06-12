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
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * A file system exposing Java classpath resources. It is equivalent to the files returned by
 * [ClassLoader.getResource].
 *
 * This file system does not implement merging of multiple paths from difference resources like
 * overlapping `.jar` files.
 *
 * ResourceFileSystem excludes `.class` files.
 */
@ExperimentalFileSystem
internal class ResourceFileSystem internal constructor(
  private val classLoader: ClassLoader
) : FileSystem() {
  private var jarCache = ConcurrentHashMap<Path, FileSystem>()

  override fun canonicalize(path: Path): Path {
    return "/".toPath() / path
  }

  override fun list(dir: Path): List<Path> {
    val (fileSystem, fileSystemPath) = toSystemPath(dir) ?: return listOf()
    return fileSystem.list(fileSystemPath)
  }

  override fun openReadOnly(file: Path): FileHandle {
    val (fileSystem, fileSystemPath) = toSystemPath(file)
      ?: throw FileNotFoundException("file not found: $file")
    return fileSystem.openReadOnly(file = fileSystemPath)
  }

  override fun openReadWrite(file: Path): FileHandle {
    throw IOException("resources are not writable")
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val (fileSystem, fileSystemPath) = toSystemPath(path) ?: return null
    return fileSystem.metadataOrNull(fileSystemPath)
  }

  override fun source(file: Path): Source {
    val (fileSystem, fileSystemPath) = toSystemPath(file)
      ?: throw FileNotFoundException("file not found: $file")
    return fileSystem.source(fileSystemPath)
  }

  /**
   * Return the file system and path for a file if it is available. The FileSystem abstraction is
   * designed to hide the specifics of where files are loaded from e.g. from within a Zip file.
   */
  internal fun toSystemPath(path: Path): Pair<FileSystem, Path>? {
    val resourceName = canonicalize(path).toString().substring(1)

    val url = classLoader.getResource(resourceName)
    val urlString = url?.toString() ?: return null

    return when {
      urlString.startsWith("file:") -> Pair(SYSTEM, File(url.toURI()).toOkioPath())
      urlString.startsWith("jar:file:") -> {
        val (jarPath, resourcePath) = jarFilePaths(urlString)
        Pair(jarPath.openZip(), resourcePath)
      }
      else -> null // Silently ignore unexpected URLs.
    }
  }

  /**
   * Returns a string like `/tmp/foo.jar` from a URL string like
   * `jar:file:/tmp/foo.jar!/META-INF/MANIFEST.MF`. This strips the scheme prefix `jar:file:` and an
   * optional path suffix like `!/META-INF/MANIFEST.MF`.
   */
  private fun jarFilePaths(jarFileUrl: String): Pair<Path, Path> {
    val suffixStart = jarFileUrl.lastIndexOf("!")
    require(suffixStart != -1) { "Not a complete JAR path: $jarFileUrl" }
    val jarPath = jarFileUrl.substring("jar:file:".length, suffixStart).toPath()
    val resourcePath = jarFileUrl.substring(suffixStart + 1).toPath()
    return jarPath to resourcePath
  }

  private fun Path.openZip(): FileSystem {
    val existing = jarCache[this]
    if (existing != null) return existing

    val created = openZip(
      zipPath = this,
      fileSystem = SYSTEM,
      predicate = { zipEntry -> !zipEntry.canonicalPath.name.endsWith(".class", ignoreCase = true) }
    )

    // Recover from a race if two threads open the same zip at the same time.
    val replaced = jarCache.putIfAbsent(this, created) ?: return created

    // TODO: close created?
    return replaced
  }

  override fun sink(file: Path): Sink = throw IOException("$this is read-only")

  override fun appendingSink(file: Path): Sink =
    throw IOException("$this is read-only")

  override fun createDirectory(dir: Path): Unit =
    throw IOException("$this is read-only")

  override fun atomicMove(source: Path, target: Path): Unit =
    throw IOException("$this is read-only")

  override fun delete(path: Path): Unit = throw IOException("$this is read-only")
}
