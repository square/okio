/*
 * Copyright (C) 2020 Square, Inc.
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

import okio.internal.commonCompareTo
import okio.internal.commonEquals
import okio.internal.commonHashCode
import okio.internal.commonIsAbsolute
import okio.internal.commonIsRelative
import okio.internal.commonIsRoot
import okio.internal.commonName
import okio.internal.commonNameBytes
import okio.internal.commonNormalized
import okio.internal.commonParent
import okio.internal.commonRelativeTo
import okio.internal.commonResolve
import okio.internal.commonRoot
import okio.internal.commonSegments
import okio.internal.commonSegmentsBytes
import okio.internal.commonToPath
import okio.internal.commonToString
import okio.internal.commonVolumeLetter

actual class Path internal actual constructor(
  internal actual val bytes: ByteString,
) : Comparable<Path> {
  actual val root: Path?
    get() = commonRoot()

  actual val segments: List<String>
    get() = commonSegments()

  actual val segmentsBytes: List<ByteString>
    get() = commonSegmentsBytes()

  actual val isAbsolute: Boolean
    get() = commonIsAbsolute()

  actual val isRelative: Boolean
    get() = commonIsRelative()

  actual val volumeLetter: Char?
    get() = commonVolumeLetter()

  actual val nameBytes: ByteString
    get() = commonNameBytes()

  actual val name: String
    get() = commonName()

  actual val parent: Path?
    get() = commonParent()

  actual val isRoot: Boolean
    get() = commonIsRoot()

  actual operator fun div(child: String): Path = commonResolve(child, normalize = false)

  actual operator fun div(child: ByteString): Path = commonResolve(child, normalize = false)

  actual operator fun div(child: Path): Path = commonResolve(child, normalize = false)

  actual fun resolve(child: String, normalize: Boolean): Path =
    commonResolve(child, normalize = normalize)

  actual fun resolve(child: ByteString, normalize: Boolean): Path =
    commonResolve(child, normalize = normalize)

  actual fun resolve(child: Path, normalize: Boolean): Path =
    commonResolve(child = child, normalize = normalize)

  actual fun relativeTo(other: Path): Path = commonRelativeTo(other)

  actual fun normalized(): Path = commonNormalized()

  actual override fun compareTo(other: Path): Int = commonCompareTo(other)

  actual override fun equals(other: Any?): Boolean = commonEquals(other)

  actual override fun hashCode() = commonHashCode()

  actual override fun toString() = commonToString()

  actual companion object {
    actual val DIRECTORY_SEPARATOR: String = PLATFORM_DIRECTORY_SEPARATOR

    actual fun String.toPath(normalize: Boolean): Path = commonToPath(normalize)
  }
}
