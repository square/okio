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
import okio.ZipBuilder
import okio.randomToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URLClassLoader
import kotlin.reflect.KClass
import kotlin.test.fail

@ExperimentalFileSystem
class ResourceFileSystemTest {
  private val fileSystem = FileSystem.RESOURCES as ResourceFileSystem
  private var base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / randomToken(16)

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
  fun testSingleArchive() {
    val zipPath = ZipBuilder(base)
      .addEntry("hello.txt", "Hello World")
      .addEntry("directory/subdirectory/child.txt", "Another file!")
      .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
      .build()
    val resourceFileSystem = ResourceFileSystem(
      classLoader = URLClassLoader(arrayOf(zipPath.toFile().toURI().toURL()), null)
    )

    assertThat(resourceFileSystem.read("hello.txt".toPath()) { readUtf8() })
      .isEqualTo("Hello World")

    assertThat(resourceFileSystem.read("directory/subdirectory/child.txt".toPath()) { readUtf8() })
      .isEqualTo("Another file!")

    assertThat(resourceFileSystem.list("/".toPath()))
      .hasSameElementsAs(listOf("/META-INF".toPath(), "/hello.txt".toPath(), "/directory".toPath()))
    assertThat(resourceFileSystem.list("/directory".toPath()))
      .containsExactly("/directory/subdirectory".toPath())
    assertThat(resourceFileSystem.list("/directory/subdirectory".toPath()))
      .containsExactly("/directory/subdirectory/child.txt".toPath())

    val metadata = resourceFileSystem.metadata(".".toPath())
    assertThat(metadata.isDirectory).isTrue()
  }

  @Test
  fun testDirectoryAndJarOverlap() {
    val filesAPath = base / "filesA"
    FileSystem.SYSTEM.createDirectories(filesAPath / "colors")
    FileSystem.SYSTEM.write(filesAPath / "colors" / "red.txt") { writeUtf8("Apples are red") }
    FileSystem.SYSTEM.write(filesAPath / "colors" / "green.txt") { writeUtf8("Grass is green") }
    val zipBPath = ZipBuilder(base)
      .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
      .addEntry("colors/blue.txt", "The sky is blue")
      .addEntry("colors/green.txt", "Limes are green")
      .build()

    val resourceFileSystem = ResourceFileSystem(
      classLoader = URLClassLoader(
        arrayOf(
          filesAPath.toFile().toURI().toURL(),
          zipBPath.toFile().toURI().toURL(),
        ),
        null
      )
    )

    assertThat(resourceFileSystem.read("/colors/red.txt".toPath()) { readUtf8() })
      .isEqualTo("Apples are red")
    assertThat(resourceFileSystem.read("/colors/green.txt".toPath()) { readUtf8() })
      .isEqualTo("Grass is green")
    assertThat(resourceFileSystem.read("/colors/blue.txt".toPath()) { readUtf8() })
      .isEqualTo("The sky is blue")

    assertThat(resourceFileSystem.list("/".toPath()))
      .hasSameElementsAs(listOf("/META-INF".toPath(), "/colors".toPath()))
    assertThat(resourceFileSystem.list("/colors".toPath())).hasSameElementsAs(
      listOf(
        "/colors/red.txt".toPath(),
        "/colors/green.txt".toPath(),
        "/colors/blue.txt".toPath()
      )
    )

    assertThat(resourceFileSystem.metadata("/".toPath()).isDirectory).isTrue()
    assertThat(resourceFileSystem.metadata("/colors".toPath()).isDirectory).isTrue()
  }

  @Test
  fun testDirectoryAndDirectoryOverlap() {
    val filesAPath = base / "filesA"
    FileSystem.SYSTEM.createDirectories(filesAPath / "colors")
    FileSystem.SYSTEM.write(filesAPath / "colors" / "red.txt") { writeUtf8("Apples are red") }
    FileSystem.SYSTEM.write(filesAPath / "colors" / "green.txt") { writeUtf8("Grass is green") }
    val filesBPath = base / "filesB"
    FileSystem.SYSTEM.createDirectories(filesBPath / "colors")
    FileSystem.SYSTEM.write(filesBPath / "colors" / "blue.txt") { writeUtf8("The sky is blue") }
    FileSystem.SYSTEM.write(filesBPath / "colors" / "green.txt") { writeUtf8("Limes are green") }

    val resourceFileSystem = ResourceFileSystem(
      classLoader = URLClassLoader(
        arrayOf(
          filesAPath.toFile().toURI().toURL(),
          filesBPath.toFile().toURI().toURL(),
        ),
        null
      )
    )

    assertThat(resourceFileSystem.read("/colors/red.txt".toPath()) { readUtf8() })
      .isEqualTo("Apples are red")
    assertThat(resourceFileSystem.read("/colors/green.txt".toPath()) { readUtf8() })
      .isEqualTo("Grass is green")
    assertThat(resourceFileSystem.read("/colors/blue.txt".toPath()) { readUtf8() })
      .isEqualTo("The sky is blue")

    assertThat(resourceFileSystem.list("/".toPath()))
      .hasSameElementsAs(listOf("/colors".toPath()))
    assertThat(resourceFileSystem.list("/colors".toPath())).hasSameElementsAs(
      listOf(
        "/colors/red.txt".toPath(),
        "/colors/green.txt".toPath(),
        "/colors/blue.txt".toPath()
      )
    )

    assertThat(resourceFileSystem.metadata("/".toPath()).isDirectory).isTrue()
    assertThat(resourceFileSystem.metadata("/colors".toPath()).isDirectory).isTrue()
  }

  @Test
  fun testJarAndJarOverlap() {
    val zipAPath = ZipBuilder(base)
      .addEntry("colors/red.txt", "Apples are red")
      .addEntry("colors/green.txt", "Grass is green")
      .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
      .build()
    val zipBPath = ZipBuilder(base)
      .addEntry("colors/blue.txt", "The sky is blue")
      .addEntry("colors/green.txt", "Limes are green")
      .addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
      .build()
    val resourceFileSystem = ResourceFileSystem(
      classLoader = URLClassLoader(
        arrayOf(
          zipAPath.toFile().toURI().toURL(),
          zipBPath.toFile().toURI().toURL(),
        ),
        null
      )
    )

    assertThat(resourceFileSystem.read("/colors/red.txt".toPath()) { readUtf8() })
      .isEqualTo("Apples are red")
    assertThat(resourceFileSystem.read("/colors/green.txt".toPath()) { readUtf8() })
      .isEqualTo("Grass is green")
    assertThat(resourceFileSystem.read("/colors/blue.txt".toPath()) { readUtf8() })
      .isEqualTo("The sky is blue")

    assertThat(resourceFileSystem.list("/".toPath()))
      .hasSameElementsAs(listOf("/META-INF".toPath(), "/colors".toPath()))
    assertThat(resourceFileSystem.list("/colors".toPath())).hasSameElementsAs(
      listOf(
        "/colors/red.txt".toPath(),
        "/colors/green.txt".toPath(),
        "/colors/blue.txt".toPath()
      )
    )

    assertThat(resourceFileSystem.metadata("/".toPath()).isDirectory).isTrue()
    assertThat(resourceFileSystem.metadata("/colors".toPath()).isDirectory).isTrue()
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
