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
import java.net.URI
import java.net.URL

/**
 * A file system exposing Java classpath resources. It is equivalent to the files returned by
 * [ClassLoader.getResource] but supports extra features like [metadataOrNull] and [list].
 *
 * If `.jar` files overlap, this returns an arbitrary element. For overlapping directories it unions
 * their contents.
 *
 * ResourceFileSystem excludes `.class` files.
 *
 * This file system is read-only.
 */
@ExperimentalFileSystem
internal class ResourceFileSystem internal constructor(
  classLoader: ClassLoader,
  indexEagerly: Boolean,
) : FileSystem() {
  private val roots: List<Pair<FileSystem, Path>> by lazy { classLoader.toClasspathRoots() }

  init {
    if (indexEagerly) {
      roots.size
    }
  }

  override fun canonicalize(path: Path): Path {
    return ROOT / path
  }

  override fun list(dir: Path): List<Path> {
    val relativePath = dir.toRelativePath()
    val result = mutableSetOf<Path>()
    var foundAny = false
    for ((fileSystem, base) in roots) {
      try {
        result += fileSystem.list(base / relativePath)
          .filter { keepPath(it) }
          .map { it.removeBase(base) }
        foundAny = true
      } catch (_: IOException) {
      }
    }
    if (!foundAny) throw FileNotFoundException("file not found: $dir")
    return result.toList()
  }

  override fun openReadOnly(file: Path): FileHandle {
    if (!keepPath(file)) throw FileNotFoundException("file not found: $file")
    val relativePath = file.toRelativePath()
    for ((fileSystem, base) in roots) {
      try {
        return fileSystem.openReadOnly(base / relativePath)
      } catch (_: FileNotFoundException) {
      }
    }
    throw FileNotFoundException("file not found: $file")
  }

  override fun openReadWrite(file: Path): FileHandle {
    throw IOException("resources are not writable")
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    if (!keepPath(path)) return null
    val relativePath = path.toRelativePath()
    for ((fileSystem, base) in roots) {
      return fileSystem.metadataOrNull(base / relativePath) ?: continue
    }
    return null
  }

  override fun source(file: Path): Source {
    if (!keepPath(file)) throw FileNotFoundException("file not found: $file")
    val relativePath = file.toRelativePath()
    for ((fileSystem, base) in roots) {
      try {
        return fileSystem.source(base / relativePath)
      } catch (_: FileNotFoundException) {
      }
    }
    throw FileNotFoundException("file not found: $file")
  }

  override fun sink(file: Path): Sink = throw IOException("$this is read-only")

  override fun appendingSink(file: Path): Sink =
    throw IOException("$this is read-only")

  override fun createDirectory(dir: Path): Unit =
    throw IOException("$this is read-only")

  override fun atomicMove(source: Path, target: Path): Unit =
    throw IOException("$this is read-only")

  override fun delete(path: Path): Unit = throw IOException("$this is read-only")

  override fun createSymlink(source: Path, target: Path): Unit =
    throw IOException("$this is read-only")

  private fun Path.toRelativePath(): String = canonicalize(this).toString().substring(1)

  private companion object {
    val ROOT = "/".toPath()

    fun Path.removeBase(base: Path): Path {
      val prefix = base.toString()
      return ROOT / (toString().removePrefix(prefix).replace('\\', '/'))
    }

    /**
     * Returns a search path of classpath roots. Each element contains a file system to use, and
     * the base directory of that file system to search from.
     */
    fun ClassLoader.toClasspathRoots(): List<Pair<FileSystem, Path>> {
      // We'd like to build this upon an API like ClassLoader.getURLs() but unfortunately that
      // API exists only on URLClassLoader (and that isn't the default class loader implementation).
      //
      // The closest we have is `ClassLoader.getResources("")`. It returns all classpath roots that
      // are directories but none that are .jar files. To mitigate that we also search for all
      // `META-INF/MANIFEST.MF` files, hastily assuming that every .jar file will have such an
      // entry.
      //
      // Classpath entries that aren't directories and don't have a META-INF/MANIFEST.MF file will
      // not be visible in this file system.
      return getResources("").toList().mapNotNull { it.toFileRoot() } +
        getResources("META-INF/MANIFEST.MF").toList().mapNotNull { it.toJarRoot() }
    }

    fun URL.toFileRoot(): Pair<FileSystem, Path>? {
      if (protocol != "file") return null // Ignore unexpected URLs.
      return SYSTEM to File(toURI()).toOkioPath()
    }

    fun URL.toJarRoot(): Pair<FileSystem, Path>? {
      val urlString = toString()
      if (!urlString.startsWith("jar:file:")) return null // Ignore unexpected URLs.

      // Given a URL like `jar:file:/tmp/foo.jar!/META-INF/MANIFEST.MF`, get the path to the archive
      // file, like `/tmp/foo.jar`.
      val suffixStart = urlString.lastIndexOf("!")
      if (suffixStart == -1) return null
      val path = File(URI.create(urlString.substring("jar:".length, suffixStart))).toOkioPath()
      val zip = openZip(
        zipPath = path,
        fileSystem = SYSTEM,
        predicate = { entry -> keepPath(entry.canonicalPath) }
      )
      return zip to ROOT
    }

    private fun keepPath(path: Path) = !path.name.endsWith(".class", ignoreCase = true)
  }
}
