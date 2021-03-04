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
package okio.resourcefilesystem

import okio.ExperimentalFileSystem
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.Source
import okio.internal.ReadOnlyFilesystem
import okio.zipfilesystem.ZipFileSystem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A file system exposing the traditional java classpath resources.
 *
 * Both metadata and file listings are best effort, and will work better
 * for local project paths. The file system does not handle merging of
 * multiple paths from difference resources like overlapping Jar files.
 *
 * ResourceFileSystem does not list classes, but will allow them to be read if specifically
 * fetched.
 */
@ExperimentalFileSystem
class ResourceFileSystem internal constructor(private val classLoader: ClassLoader) :
  ReadOnlyFilesystem() {
  var jarCache = ConcurrentHashMap<Path, ZipFileSystem>()

  override fun canonicalize(path: Path): Path {
    return "/".toPath() / path
  }

  override fun list(dir: Path): List<Path> {
    return toSystemPath(dir)?.let { (fileSystem, path) ->
      fileSystem.list(path).filterNot { path.name.endsWith(".class") }
    }.orEmpty()
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    return toSystemPath(path)?.let { (fileSystem, path) ->
      fileSystem.metadataOrNull(path)
    }
  }

  override fun source(file: Path): Source {
    return toSystemPath(file)?.let { (fileSystem, path) ->
      fileSystem.source(path)
    } ?: throw FileNotFoundException("file not found: $file")
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
      urlString.startsWith("file:") -> {
        val file = File(url.toURI()).toOkioPath()
        Pair(SYSTEM, file)
      }
      urlString.startsWith("jar:file:") -> {
        val (jarPath, resourcePath) = jarFilePaths(urlString)
        return Pair(jarPath.openZip(), resourcePath)
      }
      else -> {
        // Silently ignore unexpected URLs.
        null
      }
    }
  }

  /**
   * Returns a string like `/tmp/foo.jar` from a URL string like
   * `jar:file:/tmp/foo.jar!/META-INF/MANIFEST.MF`. This strips the scheme prefix `jar:file:` and an
   * optional path suffix like `!/META-INF/MANIFEST.MF`.
   */
  private fun jarFilePaths(jarFileUrl: String): Pair<Path, Path> {
    val suffixStart = jarFileUrl.lastIndexOf("!")
    if (suffixStart == -1) throw IllegalStateException("Not a complete JAR path: $jarFileUrl")
    val jarPath = jarFileUrl.substring("jar:file:".length, suffixStart).toPath()
    val resourcePath = jarFileUrl.substring(suffixStart + 1).toPath()
    return Pair(jarPath, resourcePath)
  }

  private fun Path.openZip(): ZipFileSystem {
    return jarCache.computeIfAbsent(this) {
      okio.zipfilesystem.open(it, SYSTEM)
    }
  }

  companion object {
    /**
     * A flat view of a presumed typical non-container classpath.
     *
     * More involved classloader scenarios like in a container should be handled separately.
     */
    val SYSTEM_RESOURCES = ResourceFileSystem(ResourceFileSystem.javaClass.classLoader)
  }
}