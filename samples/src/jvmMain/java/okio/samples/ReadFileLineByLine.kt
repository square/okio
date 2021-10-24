/*
 * Copyright (C) 2018 Square, Inc.
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
package okio.samples

import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer

@Throws(IOException::class)
fun readLines(path: Path) {
  FileSystem.SYSTEM.source(path).use { fileSource ->
    fileSource.buffer().use { bufferedFileSource ->
      while (true) {
        val line = bufferedFileSource.readUtf8Line() ?: break
        if ("square" in line) {
          println(line)
        }
      }
    }
  }
}

fun main() {
  readLines("../README.md".toPath())
}
