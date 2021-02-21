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
package okio.resourcefilesystem

import okio.ExperimentalFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import kotlin.reflect.KClass


@ExperimentalFileSystem
class ResourceFileSystemTest {
  val fileSystem = ResourceFileSystem()

  @Test
  fun testResourceA() {
    val path = "okio/resourcefilesystem/a.txt".toPath()

    val metadata = fileSystem.metadataOrNull(path)!!

    assertThat(metadata.size).isEqualTo(1L)
    assertThat(metadata.isRegularFile).isTrue()
    assertThat(metadata.isDirectory).isFalse()

    val content = fileSystem.read(path) { readUtf8() }

    assertThat(content).isEqualTo("a")
  }

  @Test
  fun testResourceB() {
    val path = "okio/resourcefilesystem/b/b.txt".toPath()

    val metadata = fileSystem.metadataOrNull(path)!!

    assertThat(metadata.size).isEqualTo(3L)
    assertThat(metadata.isRegularFile).isTrue()
    assertThat(metadata.isDirectory).isFalse()

    val content = fileSystem.read(path) { readUtf8() }

    assertThat(content).isEqualTo("b/b")
  }

  @Test
  fun testResourceMissing() {
    val path = "okio/resourcefilesystem/b/c.txt".toPath()

    try {
      fileSystem.metadataOrNull(path)
    } catch (ioe: IOException) {
      assertThat(ioe.message).matches("metadata for .* not supported")
    }

    try {
      fileSystem.read(path) { readUtf8() }
    } catch (ioe: IOException) {
      assertThat(ioe.message).isEqualTo("file not found: okio/resourcefilesystem/b/c.txt")
    }
  }

  @Test
  fun testProjectIsListable() {
    val path = "okio/resourcefilesystem/b/".toPath()

    val metadata = fileSystem.metadataOrNull(path)!!

    assertThat(metadata.isDirectory).isTrue()
    assertThat(metadata.createdAtMillis).isGreaterThan(1L)

    try {
      fileSystem.list(path)
    } catch (ioe: IOException) {
      assertThat(ioe.message).isEqualTo("file not found: b/c.txt")
    }
  }

  @Test
  fun testSystemPath() {
    val pwd = File(".").absolutePath

    assertThat(fileSystem.toSystemPath("okio/resourcefilesystem/a.txt".toPath()).toString()).matches(
      "$pwd.*/resourcefilesystem/a.txt"
    )
    assertThat(fileSystem.toSystemPath("okio/resourcefilesystem/b/".toPath()).toString()).matches(
      "$pwd.*/resourcefilesystem/b"
    )
    assertThat(fileSystem.toSystemPath("okio/resourcefilesystem/b/b.txt".toPath()).toString()).matches(
      "$pwd.*/resourcefilesystem/b/b.txt"
    )
    assertThat(fileSystem.toSystemPath("LICENSE-junit.txt".toPath())).isNull()
  }

  @Test
  fun testResourceFromJar() {
    val path = "LICENSE-junit.txt".toPath()

    try {
      fileSystem.metadataOrNull(path)
    } catch (ioe: IOException) {
      assertThat(ioe.message).matches("metadata for .* not supported")
    }

    // TODO supported in theory but unable to determine files and directories
    // val metadata = fileSystem.metadataOrNull(path)!!
    //
    // assertThat(metadata.size).isGreaterThan(10000L)
    // assertThat(metadata.isRegularFile).isTrue()
    // assertThat(metadata.isDirectory).isFalse()

    val content = fileSystem.read(path) { readUtf8Line() }

    assertThat(content).isEqualTo("JUnit")
  }

  @Test
  fun testDirectoryFromJar() {
    val path = "org/junit/".toPath()

    try {
      fileSystem.metadataOrNull(path)
    } catch (ioe: IOException) {
      assertThat(ioe.message).matches("metadata for .* not supported")
    }

    try {
      fileSystem.list(path)
    } catch (ioe: IOException) {
      assertThat(ioe.message).isEqualTo("not listable")
    }
  }

  @Test
  fun testUnconstrainedResources() {
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem".toPath())).isNotNull()
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem/a.txt".toPath())).isNotNull()
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem/b".toPath())).isNotNull()
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem/b/b.txt".toPath())).isNotNull()
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem/x/x.txt".toPath())).isNotNull()
    assertThat(fileSystem.canonicalize("LICENSE-junit.txt".toPath())).isNotNull()
  }

  @Test
  fun testConstrainedResources() {
    val fileSystem = ResourceFileSystem(paths = listOf("okio/resourcefilesystem".toPath()))

    assertThat(fileSystem.canonicalize("okio/resourcefilesystem".toPath())).isNotNull()
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem/a.txt".toPath())).isNotNull()
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem/b".toPath())).isNotNull()
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem/b/b.txt".toPath())).isNotNull()
    // TODO consider whether this should fail? Is the filter a path, or the files in that path.
    assertThat(fileSystem.canonicalize("okio/resourcefilesystem/x/x.txt".toPath())).isNotNull()

    try {
      assertThat(fileSystem.canonicalize("LICENSE-junit.txt".toPath()))
    } catch (ioe: IOException) {
      assertThat(ioe.message).isEqualTo(
        "Requested path LICENSE-junit.txt is not within resource filesystem [okio/resourcefilesystem]"
      )
    }
  }

  @Test
  fun packagePath() {
    val path = ResourceFileSystemTest::class.java.`package`.toPath()

    assertThat((path / "a.txt").toString()).isEqualTo("okio/resourcefilesystem/a.txt")
  }

  @Test
  fun classResource() {
    val path = ResourceFileSystemTest::class.packagePath!!

    assertThat((path / "a.txt").toString()).isEqualTo("okio/resourcefilesystem/a.txt")
  }
}