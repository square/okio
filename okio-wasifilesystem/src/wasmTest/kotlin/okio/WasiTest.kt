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

import WasiFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.Path.Companion.toPath

class WasiTest {
  private val fileSystem = WasiFileSystem
  private val base: Path = "/tmp".toPath() / "${this::class.simpleName}-${randomToken(16)}"

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test
  fun createDirectory() {
    fileSystem.createDirectory(base / "child")
  }

  @Test
  fun writeFiles() {
    fileSystem.write(base / "hello.txt") {
      writeUtf8("hello\n")
    }
  }

  @Test
  fun listDirectory() {
    fileSystem.write(base / "a") {
      writeUtf8("this file has a 1-byte file name")
    }
    fileSystem.write(base / "a.txt") {
      writeUtf8("this file has a 5-byte file name")
    }

    assertEquals(
      listOf(
        base / "a",
        base / "a.txt",
      ),
      fileSystem.list(base),
    )
  }
}
