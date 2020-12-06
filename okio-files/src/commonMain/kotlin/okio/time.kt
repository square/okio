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

import kotlinx.datetime.Instant

fun FileMetadata(
  isRegularFile: Boolean = false,
  isDirectory: Boolean = false,
  size: Long? = null,
  createdAt: Instant? = null,
  lastModifiedAt: Instant? = null,
  lastAccessedAt: Instant? = null
): FileMetadata {
  return FileMetadata(
    isRegularFile = isRegularFile,
    isDirectory = isDirectory,
    size = size,
    createdAtMillis = createdAt?.toEpochMilliseconds(),
    lastModifiedAtMillis = lastModifiedAt?.toEpochMilliseconds(),
    lastAccessedAtMillis = lastAccessedAt?.toEpochMilliseconds())
}

val FileMetadata.createdAt: Instant?
  get() {
    val createdAt = createdAtMillis ?: return null
    return Instant.fromEpochMilliseconds(createdAt)
  }

val FileMetadata.lastModifiedAt: Instant?
  get() {
    val lastModifiedAt = lastModifiedAtMillis ?: return null
    return Instant.fromEpochMilliseconds(lastModifiedAt)
  }

val FileMetadata.lastAccessedAt: Instant?
  get() {
    val lastAccessedAt = lastAccessedAtMillis ?: return null
    return Instant.fromEpochMilliseconds(lastAccessedAt)
  }
