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
package okio.internal

import okio.ByteString
import okio.ExperimentalFileSystem
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.ZipFileSystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.test.fail

@ExperimentalFileSystem
class ResourceFileSystemTest {
  private val fileSystem = FileSystem.RESOURCES as ResourceFileSystem

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

    assertThat(fileSystem.metadataOrNull(path)).isNull()

    try {
      fileSystem.read(path) { readUtf8() }
      fail()
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

    assertThat(fileSystem.list(path).map { it.name }).containsExactly("b.txt")
  }

  @Test
  fun testSystemPath() {
    val pwd = FileSystem.SYSTEM.canonicalize(".".toPath())
    val slash = Path.DIRECTORY_SEPARATOR

    val (aTxtFs, aTxtPath) = fileSystem.toSystemPath("okio/resourcefilesystem/a.txt".toPath())!!
    assertThat(aTxtFs).isSameAs(FileSystem.SYSTEM)
    assertThat(aTxtPath.toString()).startsWith(pwd.toString())
    assertThat(aTxtPath.toString()).endsWith("resourcefilesystem${slash}a.txt")

    val (bDirFs, bDirPath) = fileSystem.toSystemPath("okio/resourcefilesystem/b/".toPath())!!
    assertThat(bDirFs).isSameAs(FileSystem.SYSTEM)
    assertThat(bDirPath.toString()).startsWith(pwd.toString())
    assertThat(bDirPath.toString()).endsWith("resourcefilesystem${slash}b")

    val (bTxtFs, bTxtPath) = fileSystem.toSystemPath("okio/resourcefilesystem/b/b.txt".toPath())!!
    assertThat(bTxtFs).isSameAs(FileSystem.SYSTEM)
    assertThat(bTxtPath.toString()).startsWith(pwd.toString())
    assertThat(bTxtPath.toString()).endsWith("resourcefilesystem${slash}b${slash}b.txt")

    val (junitJar, licensePath) = fileSystem.toSystemPath("LICENSE-junit.txt".toPath())!!
    assertThat(junitJar).isInstanceOf(ZipFileSystem::class.java)
    assertThat(licensePath.toString()).matches("/LICENSE-junit.txt")
  }

  @Test
  fun testResourceFromJar() {
    val path = "LICENSE-junit.txt".toPath()

    val metadata = fileSystem.metadataOrNull(path)!!

    assertThat(metadata.size).isGreaterThan(10000L)
    assertThat(metadata.isRegularFile).isTrue()
    assertThat(metadata.isDirectory).isFalse()

    val content = fileSystem.read(path) { readUtf8Line() }

    assertThat(content).isEqualTo("JUnit")
  }

  @Test
  fun testClassFilesOmittedFromJar() {
    assertThat(fileSystem.list("/org/junit/rules".toPath())).isEmpty()
    assertThat(fileSystem.metadataOrNull("/org/junit/Test.class".toPath())).isNull()
  }

  @Test
  fun testDirectoryFromJar() {
    val path = "org/junit/".toPath()

    val metadata = fileSystem.metadataOrNull(path)
    assertThat(metadata?.isDirectory).isTrue()

    val files = fileSystem.list(path).map { it.name }
    assertThat(files).contains("matchers", "rules")
    assertThat(files.filter { it.endsWith(".class") }).isEmpty()
  }

  @Test
  fun packagePath() {
    val path = ByteString::class.java.`package`.toPath()

    assertThat((path / "a.txt").toString())
      .isEqualTo("okio${Path.DIRECTORY_SEPARATOR}a.txt")
  }

  @Test
  fun classResource() {
    val path = ByteString::class.packagePath!!

    assertThat((path / "a.txt").toString())
      .isEqualTo("okio${Path.DIRECTORY_SEPARATOR}a.txt")
  }

  private fun Package.toPath(): Path = name.replace(".", "/").toPath()

  private val KClass<*>.packagePath: Path?
    get() = qualifiedName?.replace(".", "/")?.toPath()?.parent
}
