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

import java.io.File
import java.io.IOException
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import okio.FileHandle
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.source

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
internal class ResourceFileSystem internal constructor(
  private val classLoader: ClassLoader,
  indexEagerly: Boolean,
  private val systemFileSystem: FileSystem = SYSTEM,
) : FileSystem() {
  private val roots: List<Pair<FileSystem, Path>> by lazy { classLoader.toClasspathRoots() }

  init {
    if (indexEagerly) {
      roots.size
    }
  }

  override fun canonicalize(path: Path): Path {
    // TODO(jwilson): throw FileNotFoundException if the canonical file doesn't exist.
    return canonicalizeInternal(path)
  }

  /** Don't throw [FileNotFoundException] if the path doesn't identify a file. */
  private fun canonicalizeInternal(path: Path): Path {
    return ROOT.resolve(path, normalize = true)
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

  override fun listOrNull(dir: Path): List<Path>? {
    val relativePath = dir.toRelativePath()
    val result = mutableSetOf<Path>()
    var foundAny = false
    for ((fileSystem, base) in roots) {
      val baseResult = fileSystem.listOrNull(base / relativePath)
        ?.filter { keepPath(it) }
        ?.map { it.removeBase(base) }
      if (baseResult != null) {
        result += baseResult
        foundAny = true
      }
    }
    return if (foundAny) result.toList() else null
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

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
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
    // Make sure we have a path that doesn't start with '/'.
    val relativePath = ROOT.resolve(file).relativeTo(ROOT)
    val resource = classLoader.getResource(relativePath.toString()) ?: throw FileNotFoundException("file not found: $file")
    val urlConnection = resource.openConnection()
    if (urlConnection is JarURLConnection) {
      urlConnection.useCaches = false
    }
    return urlConnection.getInputStream().source()
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    throw IOException("$this is read-only")
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    throw IOException("$this is read-only")
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean): Unit =
    throw IOException("$this is read-only")

  override fun atomicMove(source: Path, target: Path): Unit =
    throw IOException("$this is read-only")

  override fun delete(path: Path, mustExist: Boolean): Unit =
    throw IOException("$this is read-only")

  override fun createSymlink(source: Path, target: Path): Unit =
    throw IOException("$this is read-only")

  private fun Path.toRelativePath(): String {
    val canonicalThis = canonicalizeInternal(this)
    return canonicalThis.relativeTo(ROOT).toString()
  }

  /**
   * Returns a search path of classpath roots. Each element contains a file system to use, and
   * the base directory of that file system to search from.
   */
  private fun ClassLoader.toClasspathRoots(): List<Pair<FileSystem, Path>> {
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

  private fun URL.toFileRoot(): Pair<FileSystem, Path>? {
    if (protocol != "file") return null // Ignore unexpected URLs.
    return systemFileSystem to File(toURI()).toOkioPath()
  }

  private fun URL.toJarRoot(): Pair<FileSystem, Path>? {
    val urlString = toString()
    if (!urlString.startsWith("jar:file:")) return null // Ignore unexpected URLs.

    // Given a URL like `jar:file:/tmp/foo.jar!/META-INF/MANIFEST.MF`, get the path to the archive
    // file, like `/tmp/foo.jar`.
    val suffixStart = urlString.lastIndexOf("!")
    if (suffixStart == -1) return null
    val path = File(URI.create(urlString.substring("jar:".length, suffixStart))).toOkioPath()
    val zip = openZip(
      zipPath = path,
      fileSystem = systemFileSystem,
      predicate = { entry -> keepPath(entry.canonicalPath) },
    )
    return zip to ROOT
  }

  private companion object {
    val ROOT = "/".toPath()

    fun Path.removeBase(base: Path): Path {
      val prefix = base.toString()
      return ROOT / (toString().removePrefix(prefix).replace('\\', '/'))
    }

    private fun keepPath(path: Path) = !path.name.endsWith(".class", ignoreCase = true)
  }
}
