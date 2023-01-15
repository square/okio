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

import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import org.assertj.core.api.Assertions.assertThat

class JvmTest {
  @Test
  fun baseDirectoryConsistentWithJavaIoFile() {
    assertThat(FileSystem.SYSTEM.canonicalize(".".toPath()).toString())
      .isEqualTo(File("").canonicalFile.toString())
  }

  @Test
  fun javaIoFileToOkioPath() {
    val string = "/foo/bar/baz".replace("/", Path.DIRECTORY_SEPARATOR)
    val javaIoFile = File(string)
    val okioPath = string.toPath()
    assertThat(javaIoFile.toOkioPath()).isEqualTo(okioPath)
    assertThat(okioPath.toFile()).isEqualTo(javaIoFile)
  }

  @Test
  fun nioPathToOkioPath() {
    val string = "/foo/bar/baz".replace("/", Path.DIRECTORY_SEPARATOR)
    val nioPath = Paths.get(string)
    val okioPath = string.toPath()
    assertThat(nioPath.toOkioPath()).isEqualTo(okioPath)
    assertThat(okioPath.toNioPath() as Any).isEqualTo(nioPath)
  }
}
