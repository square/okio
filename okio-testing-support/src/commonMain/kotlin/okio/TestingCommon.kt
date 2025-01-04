/*
 * Copyright (C) 2019 Square, Inc.
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

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.time.Duration
import okio.ByteString.Companion.toByteString
import okio.Path.Companion.toPath

fun Char.repeat(count: Int): String {
  return toString().repeat(count)
}

fun assertArrayEquals(a: ByteArray, b: ByteArray) {
  assertEquals(a.contentToString(), b.contentToString())
}

fun randomBytes(length: Int, seed: Int = 0): ByteString {
  val random = Random(seed)
  val randomBytes = ByteArray(length)
  random.nextBytes(randomBytes)
  return ByteString.of(*randomBytes)
}

fun randomToken(length: Int) = Random.nextBytes(length).toByteString(0, length).hex()

expect fun isBrowser(): Boolean

expect fun isWasm(): Boolean

val FileMetadata.createdAt: Instant?
  get() {
    val createdAt = createdAtMillis ?: return null
    return fromEpochMilliseconds(createdAt)
  }

val FileMetadata.lastModifiedAt: Instant?
  get() {
    val lastModifiedAt = lastModifiedAtMillis ?: return null
    return fromEpochMilliseconds(lastModifiedAt)
  }

val FileMetadata.lastAccessedAt: Instant?
  get() {
    val lastAccessedAt = lastAccessedAtMillis ?: return null
    return fromEpochMilliseconds(lastAccessedAt)
  }

/*
 * This file contains some declarations from kotlinx.datetime used by [AbstractFileSystemTest], but
 * that we can't use because that library isn't yet available for WASM. We should delete these when
 * WASM is supported in kotlinx.datetime.
 */

expect interface Clock {
  fun now(): Instant
}

expect class Instant : Comparable<Instant> {
  val epochSeconds: Long

  operator fun plus(duration: Duration): Instant

  operator fun minus(duration: Duration): Instant

  override operator fun compareTo(other: Instant): Int
}

expect fun fromEpochSeconds(
  epochSeconds: Long,
): Instant

expect fun fromEpochMilliseconds(epochMilliseconds: Long): Instant

expect val FileSystem.isFakeFileSystem: Boolean

expect val FileSystem.allowSymlinks: Boolean

expect val FileSystem.allowReadsWhileWriting: Boolean

expect var FileSystem.workingDirectory: Path

expect fun getEnv(name: String): String?

val okioRoot: Path by lazy {
  getEnv("OKIO_ROOT")!!.toPath()
}
