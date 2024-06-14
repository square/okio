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

import okio.FileSystemExtension.Mapping

/**
 * Marks an object that can be attached to a [FileSystem], and that supplements the file system's
 * capabilities.
 *
 * Implementations must support transforms to input and output paths with [Mapping]. To simplify
 * implementation, use [Mapping.NONE] by default and use [Mapping.chain] to combine mappings.
 *
 * ```kotlin
 * class DiskUsageExtension private constructor(
 *   private val mapping: Mapping,
 * ) : FileSystemExtension {
 *   constructor() : this(Mapping.NONE)
 *
 *   override fun map(outer: Mapping): FileSystemExtension {
 *     return DiskUsageExtension(mapping.chain(outer))
 *   }
 *
 *   fun sizeOnDisk(path: Path): Long {
 *     val mappedPath = mapping.mapParameter(path, "sizeOnDisk", "path")
 *     return lookUpSizeOnDisk(mappedPath)
 *   }
 *
 *   fun largestFiles(): Sequence<Path> {
 *     val largestFiles: Sequence<Path> = lookUpLargestFiles()
 *     return largestFiles.map {
 *       mapping.mapResult(it, "largestFiles")
 *     }
 *   }
 * }
 * ```
 */
interface FileSystemExtension {
  /** Returns a file system of the same type, that applies [outer] to all paths. */
  fun map(outer: Mapping): FileSystemExtension

  abstract class Mapping {
    abstract fun mapParameter(path: Path, functionName: String, parameterName: String): Path
    abstract fun mapResult(path: Path, functionName: String): Path

    fun chain(outer: Mapping): Mapping {
      val inner = this
      return object : Mapping() {
        override fun mapParameter(path: Path, functionName: String, parameterName: String): Path {
          return inner.mapParameter(
            outer.mapParameter(
              path,
              functionName,
              parameterName,
            ),
            functionName,
            parameterName,
          )
        }

        override fun mapResult(path: Path, functionName: String): Path {
          return outer.mapResult(
            inner.mapResult(
              path,
              functionName,
            ),
            functionName,
          )
        }
      }
    }

    companion object {
      val NONE = object : Mapping() {
        override fun mapParameter(path: Path, functionName: String, parameterName: String) = path
        override fun mapResult(path: Path, functionName: String) = path
      }
    }
  }
}
