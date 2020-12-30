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

import okio.Path.Companion.toPath
import java.io.File

@ExperimentalFilesystem
internal actual val PLATFORM_FILESYSTEM: Filesystem
  get() {
    try {
      Class.forName("java.nio.file.Files")
      return NioSystemFilesystem()
    } catch (e: ClassNotFoundException) {
      return JvmSystemFilesystem()
    }
  }

@ExperimentalFilesystem
internal actual val PLATFORM_TEMPORARY_DIRECTORY: Path
  get() = System.getProperty("java.io.tmpdir").toPath()

internal actual val DIRECTORY_SEPARATOR = File.separator
