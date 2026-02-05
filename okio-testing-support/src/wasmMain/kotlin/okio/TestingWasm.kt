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
@file:OptIn(ExperimentalTime::class)

package okio

import kotlin.time.ExperimentalTime

actual fun isBrowser() = false

actual fun isWasm() = true

actual val FileSystem.isFakeFileSystem: Boolean
  get() = false

actual val FileSystem.allowSymlinks: Boolean
  get() = error("unexpected call")

actual val FileSystem.allowReadsWhileWriting: Boolean
  get() = error("unexpected call")

actual var FileSystem.workingDirectory: Path
  get() = error("unexpected call")
  set(_) = error("unexpected call")
