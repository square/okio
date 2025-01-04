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
package okio

import kotlin.time.Duration

actual fun isBrowser() = false

actual fun isWasm() = true

actual interface Clock {
  actual fun now(): Instant
}

actual class Instant(
  private val epochMilliseconds: Long,
) : Comparable<Instant> {
  actual val epochSeconds: Long
    get() = epochMilliseconds / 1_000L

  actual operator fun plus(duration: Duration) =
    Instant(epochMilliseconds + duration.inWholeMilliseconds)

  actual operator fun minus(duration: Duration) =
    Instant(epochMilliseconds - duration.inWholeMilliseconds)

  actual override fun compareTo(other: Instant) =
    epochMilliseconds.compareTo(other.epochMilliseconds)
}

actual fun fromEpochSeconds(epochSeconds: Long) =
  Instant(epochSeconds * 1_000L)

actual fun fromEpochMilliseconds(epochMilliseconds: Long) =
  Instant(epochMilliseconds)

actual val FileSystem.isFakeFileSystem: Boolean
  get() = false

actual val FileSystem.allowSymlinks: Boolean
  get() = error("unexpected call")

actual val FileSystem.allowReadsWhileWriting: Boolean
  get() = error("unexpected call")

actual var FileSystem.workingDirectory: Path
  get() = error("unexpected call")
  set(_) = error("unexpected call")

actual fun getEnv(name: String): String? = error("unexpected call")
