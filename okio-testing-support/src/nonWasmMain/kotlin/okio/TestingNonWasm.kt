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
package okio

import okio.fakefilesystem.FakeFileSystem

actual typealias Clock = kotlinx.datetime.Clock

actual typealias Instant = kotlinx.datetime.Instant

actual fun fromEpochSeconds(
  epochSeconds: Long,
) = Instant.fromEpochSeconds(epochSeconds)

actual fun fromEpochMilliseconds(
  epochMilliseconds: Long,
) = Instant.fromEpochMilliseconds(epochMilliseconds)

actual val FileSystem.isFakeFileSystem: Boolean
  get() = this is FakeFileSystem

actual val FileSystem.allowSymlinks: Boolean
  get() = (this as? FakeFileSystem)?.allowSymlinks == true

actual val FileSystem.allowReadsWhileWriting: Boolean
  get() = (this as? FakeFileSystem)?.allowReadsWhileWriting == true

actual var FileSystem.workingDirectory: Path
  get() {
    return when (this) {
      is FakeFileSystem -> workingDirectory
      is ForwardingFileSystem -> delegate.workingDirectory
      else -> error("cannot get working directory: $this")
    }
  }
  set(value) {
    when (this) {
      is FakeFileSystem -> workingDirectory = value
      is ForwardingFileSystem -> delegate.workingDirectory = value
      else -> error("cannot set working directory: $this")
    }
  }
