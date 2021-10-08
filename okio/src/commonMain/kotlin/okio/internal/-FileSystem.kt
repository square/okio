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
import okio.FileMetadata
import okio.FileNotFoundException
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use

/**
 * Returns metadata of the file, directory, or object identified by [path].
 *
 * @throws IOException if [path] does not exist or its metadata cannot be read.
 */
@ExperimentalFileSystem
@Throws(IOException::class)
internal fun FileSystem.commonMetadata(path: Path): FileMetadata {
  return metadataOrNull(path) ?: throw FileNotFoundException("no such file: $path")
}

@ExperimentalFileSystem
@Throws(IOException::class)
internal fun FileSystem.commonExists(path: Path): Boolean {
  return metadataOrNull(path) != null
}

@ExperimentalFileSystem
@Throws(IOException::class)
internal fun FileSystem.commonCreateDirectories(dir: Path) {
  // Compute the sequence of directories to create.
  val directories = ArrayDeque<Path>()
  var path: Path? = dir
  while (path != null && !exists(path)) {
    directories.addFirst(path)
    path = path.parent
  }

  // Create them.
  for (toCreate in directories) {
    createDirectory(toCreate)
  }
}

@ExperimentalFileSystem
@Throws(IOException::class)
internal fun FileSystem.commonCopy(source: Path, target: Path) {
  source(source).use { bytesIn ->
    sink(target).buffer().use { bytesOut ->
      bytesOut.writeAll(bytesIn)
    }
  }
}

@ExperimentalFileSystem
@Throws(IOException::class)
internal fun FileSystem.commonDeleteRecursively(fileOrDirectory: Path) {
  val stack = ArrayDeque<Path>()
  stack += fileOrDirectory

  while (stack.isNotEmpty()) {
    val toDelete = stack.removeLast()

    val metadata = metadata(toDelete)
    val children = if (metadata.isDirectory) list(toDelete) else listOf()

    if (children.isNotEmpty()) {
      stack += toDelete
      stack += children
    } else {
      delete(toDelete)
    }
  }
}

@ExperimentalFileSystem
@Throws(IOException::class)
internal fun FileSystem.commonListRecursively(dir: Path): Sequence<Path> {
  return sequence {
    val queue = ArrayDeque<Path>()

    // Don't try/catch for the immediate children of `dir`. If it doesn't exist the sequence should
    // throw an IOException.
    val dirChildren = list(dir)
    queue += dirChildren
    yieldAll(dirChildren)

    while (true) {
      val element = queue.removeFirstOrNull() ?: break

      // For transitive children, ignore IOExceptions such as if the child is not a directory (very
      // common), or if it has since been deleted.
      try {
        val elementChildren = list(element)
        yieldAll(elementChildren)
        queue += elementChildren
      } catch (_: IOException) {
      }
    }
  }
}
