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

import java.io.File
import java.io.IOException
import okio.buffer
import okio.sink

@Throws(IOException::class)
fun writeEnv(file: File) {
  file.sink().buffer().use { sink ->
    for ((key, value) in System.getenv()) {
      sink.writeUtf8(key)
      sink.writeUtf8("=")
      sink.writeUtf8(value)
      sink.writeUtf8("\n")
    }
  }
}

fun main() {
  writeEnv(File("env.txt"))
}
