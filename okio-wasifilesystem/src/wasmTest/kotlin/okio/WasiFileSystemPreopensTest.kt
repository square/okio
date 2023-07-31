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

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import okio.Path.Companion.toPath

/**
 * Confirm the [WasiFileSystem] can operate on different preopened directories independently.
 *
 * This tracks the `preopens` attribute in `.mjs` script in `okio-wasifilesystem/build.gradle.kts`.
 */
class WasiFileSystemPreopensTest {
  private val fileSystem = WasiFileSystem
  private val testId = "${this::class.simpleName}-${randomToken(16)}"
  private val baseA: Path = "/a".toPath() / testId
  private val baseB: Path = "/b".toPath() / testId

  @BeforeTest
  fun setUp() {
    fileSystem.createDirectory(baseA)
    fileSystem.createDirectory(baseB)
  }

  @Test
  fun operateOnPreopens() {
    fileSystem.write(baseA / "a.txt") {
      writeUtf8("hello world a")
    }
    fileSystem.write(baseB / "b.txt") {
      writeUtf8("bello burld")
    }
    assertEquals(
      "hello world a".length.toLong(),
      fileSystem.metadata(baseA / "a.txt").size,
    )
    assertEquals(
      "bello burld".length.toLong(),
      fileSystem.metadata(baseB / "b.txt").size,
    )
  }

  @Test
  fun operateAcrossPreopens() {
    fileSystem.write(baseA / "a.txt") {
      writeUtf8("hello world")
    }

    fileSystem.atomicMove(baseA / "a.txt", baseB / "b.txt")

    assertEquals(
      "hello world",
      fileSystem.read(baseB / "b.txt") {
        readUtf8()
      },
    )
  }

  @Test
  fun cannotOperateOutsideOfPreopens() {
    val noPreopen = "/c".toPath() / testId
    assertFailsWith<FileNotFoundException> {
      fileSystem.createDirectory(noPreopen)
    }
    assertFailsWith<FileNotFoundException> {
      fileSystem.sink(noPreopen)
    }
    assertNull(fileSystem.metadataOrNull(noPreopen))
    assertFailsWith<FileNotFoundException> {
      fileSystem.metadata(noPreopen)
    }
    assertNull(fileSystem.listOrNull(noPreopen))
    assertFailsWith<FileNotFoundException> {
      fileSystem.list(noPreopen)
    }
  }
}
