/*
 * Copyright (C) 2026 Square, Inc.
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

import okio.Closeable
import okio.Path
import platform.posix.closedir
import platform.posix.opendir
import platform.posix.readdir

internal actual class PosixDirectory actual constructor(path: Path) : Closeable {
  private val dir = opendir(path.toString())
  actual val isInvalid get() = dir == null
  actual fun nextEntry() = readdir(dir)
  actual override fun close() {
    closedir(dir) // Ignore errno from closedir.
  }
}
