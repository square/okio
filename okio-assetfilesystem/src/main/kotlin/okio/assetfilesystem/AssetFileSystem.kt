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
package okio.assetfilesystem

import android.content.res.AssetManager
import java.io.FileNotFoundException
import java.io.IOException
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.source

/**
 * Expose this [AssetManager] as an Okio [FileSystem].
 *
 * Note: Assets are a read-only view on a file system and so any attempt to mutate
 * will throw an [IOException].
 */
fun AssetManager.asFileSystem(): FileSystem = AssetFileSystem(this)

private class AssetFileSystem(
  private val assets: AssetManager,
) : FileSystem() {
  override fun canonicalize(path: Path): Path {
    val canonical = canonicalizeInternal(path)
    if (canonical.existsInternal()) {
      return canonical
    }
    throw FileNotFoundException("$path")
  }

  private fun canonicalizeInternal(path: Path) = ROOT.resolve(path, normalize = true)

  private fun Path.toAssetRelativePathString(): String {
    return toString().removePrefix("/")
  }

  /**
   * Determine if [this] is a valid path to a file or directory.
   *
   * If this function returns true, a call to [AssetManager.open] will either return successfully
   * or throw [FileNotFoundException] based on whether [this] is a file or directory, respectively.
   */
  private fun Path.existsInternal(): Boolean {
    if (this == ROOT) {
      return true
    }

    // Both non-existent paths and paths to existing files return an empty array when listing.
    // Determine if a path exists by checking if its name is present in the parent's list.
    val parent = checkNotNull(parent) { "Path has no parent. Did you canonicalize? $this" }
    val children = assets.list(parent.toAssetRelativePathString()).orEmpty()
    return name in children
  }

  override fun metadataOrNull(path: Path): FileMetadata? {
    val canonical = canonicalizeInternal(path)
    if (canonical.existsInternal()) {
      val pathString = canonical.toAssetRelativePathString()
      return try {
        assets.open(pathString).close()
        FileMetadata(
          isRegularFile = true,
          isDirectory = false,
        )
      } catch (_: FileNotFoundException) {
        FileMetadata(
          isRegularFile = false,
          isDirectory = true,
        )
      }
    }
    return null
  }

  override fun list(dir: Path): List<Path> {
    val canonical = canonicalizeInternal(dir)
    if (canonical.existsInternal()) {
      val pathString = canonical.toAssetRelativePathString()
      try {
        // This will throw if the path points to a file.
        assets.open(pathString).close()
      } catch (_: FileNotFoundException) {
        return assets.list(pathString)
          ?.map { it.toPath() }
          .orEmpty()
      }
    }
    throw FileNotFoundException("$dir")
  }

  override fun listOrNull(dir: Path): List<Path>? {
    return try {
      list(dir)
    } catch (_: IOException) {
      null
    }
  }

  override fun openReadOnly(file: Path): FileHandle {
    TODO("not implemented yet!")
  }

  override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
    throw IOException("asset file systems are read-only")
  }

  override fun source(file: Path): Source {
    return assets.open(canonicalizeInternal(file).toAssetRelativePathString()).source()
  }

  override fun sink(file: Path, mustCreate: Boolean): Sink {
    throw IOException("asset file systems are read-only")
  }

  override fun appendingSink(file: Path, mustExist: Boolean): Sink {
    throw IOException("asset file systems are read-only")
  }

  override fun createDirectory(dir: Path, mustCreate: Boolean) {
    throw IOException("asset file systems are read-only")
  }

  override fun atomicMove(source: Path, target: Path) {
    throw IOException("asset file systems are read-only")
  }

  override fun delete(path: Path, mustExist: Boolean) {
    throw IOException("asset file systems are read-only")
  }

  override fun createSymlink(source: Path, target: Path) {
    throw IOException("asset file systems are read-only")
  }

  private companion object {
    val ROOT = "/".toPath()
  }
}
