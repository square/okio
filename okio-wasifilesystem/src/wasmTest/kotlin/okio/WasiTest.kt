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

import kotlin.test.Test
import kotlin.test.assertEquals
import okio.Path.Companion.toPath
import okio.internal.preview1.fdReadDir
import okio.internal.preview1.oflag_directory
import okio.internal.preview1.pathOpen
import okio.internal.preview1.right_fd_readdir

class WasiTest {
  @Test
  fun happyPath() {
    val fd = pathOpen(
      path = "/okio/okio-wasifilesystem/src/wasmTest/sampleData",
      oflags = oflag_directory,
      rightsBase = right_fd_readdir,
    )

    val expectedFileNames = listOf(
      "a".toPath(),
      "a.txt".toPath(),
    )
    val dir = fdReadDir(fd)

    assertEquals(
      expectedFileNames,
      dir.map { it.name }.sorted(),
    )
  }
}
