/*
 * Copyright (C) 2024 Square, Inc.
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

/**
 * Marks an object that can be attached to a [FileSystem], and that supplements the file system's
 * capabilities.
 *
 * Implementations must support transforms to input and output paths with [PathMapper]. To simplify
 * implementation, use [PathMapper.NONE] by default and use [chain] to combine mappers.
 *
 * ```kotlin
 * class DiskUsageExtension private constructor(
 *   private val pathMapper: PathMapper,
 * ) : FileSystemExtension {
 *   constructor() : this(PathMapper.NONE)
 *
 *   override fun map(pathMapper: PathMapper): FileSystemExtension {
 *     return DiskUsageExtension(chain(pathMapper, this.pathMapper))
 *   }
 *
 *   fun sizeOnDisk(path: Path): Long {
 *     val mappedPath = pathMapper.onPathParameter(path, "sizeOnDisk", "path")
 *     return lookUpSizeOnDisk(mappedPath)
 *   }
 *
 *   fun largestFiles(): Sequence<Path> {
 *     val largestFiles: Sequence<Path> = lookUpLargestFiles()
 *     return largestFiles.map {
 *       pathMapper.onPathResult(it, "largestFiles")
 *     }
 *   }
 * }
 * ```
 */
interface FileSystemExtension {
  /** Returns a file system of the same type, that applies [pathMapper] to all paths. */
  fun map(pathMapper: PathMapper) : FileSystemExtension
}
