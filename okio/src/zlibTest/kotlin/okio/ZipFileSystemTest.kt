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
package okio

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.datetime.Instant
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath

class ZipFileSystemTest {
  private val fileSystem = FileSystem.SYSTEM
  private var base = okioRoot / "okio-testing-support/src/commonMain/resources/okio/zipfilesystem"

  @Test
  fun emptyZip() {
    val zipFileSystem = fileSystem.openZip(base / "emptyZip.zip")
    assertThat(zipFileSystem.list("/".toPath())).isEmpty()
  }

  @Test
  fun emptyZipWithPrependedData() {
    val zipFileSystem = fileSystem.openZip(base / "emptyZipWithPrependedData.zip")
    assertThat(zipFileSystem.list("/".toPath())).isEmpty()
  }

  /**
   * ```
   * echo "Hello World" > hello.txt
   *
   * mkdir -p directory/subdirectory
   * echo "Another file!" > directory/subdirectory/child.txt
   *
   * zip \
   *   zipWithFiles.zip \
   *   hello.txt \
   *   directory/subdirectory/child.txt
   * ```
   */
  @Test
  fun zipWithFiles() {
    val zipPath = base / "zipWithFiles.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    assertThat(zipFileSystem.read("hello.txt".toPath()) { readUtf8() })
      .isEqualTo("Hello World")

    assertThat(zipFileSystem.read("directory/subdirectory/child.txt".toPath()) { readUtf8() })
      .isEqualTo("Another file!")

    assertThat(zipFileSystem.list("/".toPath()))
      .containsExactlyInAnyOrder("/hello.txt".toPath(), "/directory".toPath())
    assertThat(zipFileSystem.list("/directory".toPath()))
      .containsExactly("/directory/subdirectory".toPath())
    assertThat(zipFileSystem.list("/directory/subdirectory".toPath()))
      .containsExactly("/directory/subdirectory/child.txt".toPath())
  }

  /**
   * Note that the zip tool does not compress files that don't benefit from it. Examples above like
   * 'Hello World' are stored, not deflated.
   *
   * ```
   * echo "Android
   * Android
   * ... <1000 times>
   * Android
   * " > a.txt
   *
   * zip \
   *   --compression-method \
   *   deflate \
   *   zipWithDeflate.zip \
   *   a.txt
   * ```
   */
  @Test
  fun zipWithDeflate() {
    val content = "Android\n".repeat(1000)
    val zipPath = base / "zipWithDeflate.zip"
    assertThat(fileSystem.metadata(zipPath).size).isNotNull().isLessThan(content.length.toLong())
    val zipFileSystem = fileSystem.openZip(zipPath)

    assertThat(zipFileSystem.read("a.txt".toPath()) { readUtf8() })
      .isEqualTo(content)
  }

  /**
   * ```
   * echo "Android
   * Android
   * ... <1000 times>
   * Android
   * " > a.txt
   *
   * zip \
   *   --compression-method \
   *   store \
   *   zipWithStore.zip \
   *   a.txt
   * ```
   */
  @Test
  fun zipWithStore() {
    val content = "Android\n".repeat(1000)
    val zipPath = base / "zipWithStore.zip"
    assertThat(fileSystem.metadata(zipPath).size).isNotNull().isGreaterThan(content.length.toLong())
    val zipFileSystem = fileSystem.openZip(zipPath)

    assertThat(zipFileSystem.read("a.txt".toPath()) { readUtf8() })
      .isEqualTo(content)
  }

  /**
   * Confirm we can read zip files that have file comments, even if these comments are not exposed
   * in the public API.
   *
   * ```
   * echo "Android" > a.txt
   *
   * echo "Banana" > b.txt
   *
   * zip \
   *   --entry-comments \
   *   zipWithFileComments.zip \
   *   a.txt \
   *   b.txt
   * ```
   */
  @Test
  fun zipWithFileComments() {
    val zipPath = base / "zipWithFileComments.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    assertThat(zipFileSystem.read("a.txt".toPath()) { readUtf8() })
      .isEqualTo("Android")

    assertThat(zipFileSystem.read("b.txt".toPath()) { readUtf8() })
      .isEqualTo("Banana")
  }

  /**
   * ```
   * echo "Android" > a.txt
   * touch -m -t 200102030405.06 a.txt
   * touch -a -t 200102030405.07 a.txt
   *
   * echo "Banana" > b.txt
   * touch -m -t 200908070605.04 b.txt
   * touch -a -t 200908070605.03 b.txt
   *
   * zip \
   *   zipWithFileModifiedDate.zip \
   *   a.txt \
   *   b.txt
   * ```
   */
  @Test
  fun zipWithFileModifiedDate() {
    val zipPath = base / "zipWithFileModifiedDate.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    zipFileSystem.metadata("a.txt".toPath())
      .apply {
        assertThat(isRegularFile).isTrue()
        assertThat(isDirectory).isFalse()
        assertThat(size).isEqualTo(7L)
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2001-02-03T04:05:06Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("2001-02-03T04:05:07Z".toEpochMillis())
      }

    zipFileSystem.metadata("b.txt".toPath())
      .apply {
        assertThat(isRegularFile).isTrue()
        assertThat(isDirectory).isFalse()
        assertThat(size).isEqualTo(6L)
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2009-08-07T06:05:04Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("2009-08-07T06:05:03Z".toEpochMillis())
      }
  }

  /**
   * Confirm we suffer UNIX limitations on our date format.
   *
   * ```
   * echo "Android" > a.txt
   * touch -m -t 196912310000.00 a.txt
   * touch -a -t 196912300000.00 a.txt
   *
   * echo "Banana" > b.txt
   * touch -m -t 203801190314.07 b.txt
   * touch -a -t 203801190314.08 b.txt
   *
   * zip \
   *   zipWithFileOutOfBoundsModifiedDate.zip \
   *   a.txt \
   *   b.txt
   * ```
   */
  @Test
  fun zipWithFileOutOfBoundsModifiedDate() {
    val zipPath = base / "zipWithFileOutOfBoundsModifiedDate.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    println(Instant.fromEpochMilliseconds(-2147483648000L))

    zipFileSystem.metadata("a.txt".toPath())
      .apply {
        assertThat(isRegularFile).isTrue()
        assertThat(isDirectory).isFalse()
        assertThat(size).isEqualTo(7L)
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("1969-12-31T00:00:00Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("1969-12-30T00:00:00Z".toEpochMillis())
      }

    // Greater than the upper bound wraps around.
    zipFileSystem.metadata("b.txt".toPath())
      .apply {
        assertThat(isRegularFile).isTrue()
        assertThat(isDirectory).isFalse()
        assertThat(size).isEqualTo(6L)
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2038-01-19T03:14:07Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("1901-12-13T20:45:52Z".toEpochMillis())
      }
  }

  /**
   * Directories are optional in the zip file. But if we want metadata on them they must be stored.
   * Note that this test adds the directories last; otherwise adding child files to them will cause
   * their modified at times to change.
   *
   * ```
   * mkdir -p a
   * echo "Android" > a/a.txt
   * touch -m -t 200102030405.06 a
   * touch -a -t 200102030405.07 a
   *
   * mkdir -p b
   * echo "Android" > b/b.txt
   * touch -m -t 200908070605.04 b
   * touch -a -t 200908070605.03 b
   *
   * zip \
   *   zipWithDirectoryModifiedDate.zip \
   *   a/a.txt \
   *   a \
   *   b/b.txt \
   *   b
   * ```
   */
  @Test
  fun zipWithDirectoryModifiedDate() {
    val zipPath = base / "zipWithDirectoryModifiedDate.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    zipFileSystem.metadata("a".toPath())
      .apply {
        assertThat(isRegularFile).isFalse()
        assertThat(isDirectory).isTrue()
        assertThat(size).isNull()
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2001-02-03T04:05:06Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("2001-02-03T04:05:07Z".toEpochMillis())
      }
    assertThat(zipFileSystem.list("a".toPath())).containsExactly("/a/a.txt".toPath())

    zipFileSystem.metadata("b".toPath())
      .apply {
        assertThat(isRegularFile).isFalse()
        assertThat(isDirectory).isTrue()
        assertThat(size).isNull()
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2009-08-07T06:05:04Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("2009-08-07T06:05:03Z".toEpochMillis())
      }
    assertThat(zipFileSystem.list("b".toPath())).containsExactly("/b/b.txt".toPath())
  }

  /**
   * ```
   * mkdir -p a
   * echo "Android" > a/a.txt
   * touch -m -t 197001010001.00 a/a.txt
   * touch -a -t 197001010002.00 a/a.txt
   *
   * zip \
   *   zipWithModifiedDate.zip \
   *   a/a.txt
   * ```
   */
  @Test
  fun zipWithModifiedDate() {
    val zipPath = base / "zipWithModifiedDate.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    zipFileSystem.metadata("a/a.txt".toPath())
      .apply {
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("1970-01-01T00:01:00Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("1970-01-01T00:02:00Z".toEpochMillis())
      }
  }

  /**
   * Build a very small zip file with just a single empty directory.
   *
   * ```
   * mkdir -p a
   * touch -m -t 200102030405.06 a
   * touch -a -t 200102030405.07 a
   *
   * zip \
   *   zipWithEmptyDirectory.zip \
   *   a
   * ```
   */
  @Test
  fun zipWithEmptyDirectory() {
    val zipPath = base / "zipWithEmptyDirectory.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    zipFileSystem.metadata("a".toPath())
      .apply {
        assertThat(isRegularFile).isFalse()
        assertThat(isDirectory).isTrue()
        assertThat(size).isNull()
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2001-02-03T04:05:06Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("2001-02-03T04:05:07Z".toEpochMillis())
      }
    assertThat(zipFileSystem.list("a".toPath())).isEmpty()
  }

  /**
   * The `--no-dir-entries` option causes the zip file to omit the directories from the encoded
   * file. Our implementation synthesizes these missing directories automatically.
   *
   * ```
   * mkdir -p a
   * echo "Android" > a/a.txt
   *
   * mkdir -p b
   * echo "Android" > b/b.txt
   *
   * zip \
   *   --no-dir-entries \
   *   zipWithSyntheticDirectory.zip \
   *   a/a.txt \
   *   a \
   *   b/b.txt \
   *   b
   * ```
   */
  @Test
  fun zipWithSyntheticDirectory() {
    val zipPath = base / "zipWithSyntheticDirectory.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    zipFileSystem.metadata("a".toPath())
      .apply {
        assertThat(isRegularFile).isFalse()
        assertThat(isDirectory).isTrue()
        assertThat(size).isNull()
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isNull()
        assertThat(lastAccessedAtMillis).isNull()
      }
    assertThat(zipFileSystem.list("a".toPath())).containsExactly("/a/a.txt".toPath())

    zipFileSystem.metadata("b".toPath())
      .apply {
        assertThat(isRegularFile).isFalse()
        assertThat(isDirectory).isTrue()
        assertThat(size).isNull()
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isNull()
        assertThat(lastAccessedAtMillis).isNull()
      }
    assertThat(zipFileSystem.list("b".toPath())).containsExactly("/b/b.txt".toPath())
  }

  /**
   * Force a file to be encoded with zip64 metadata. We use a pipe to force the zip command to
   * create a zip64 archive; otherwise we'd need to add a very large file to get this format.
   *
   * ```
   * zip \
   *   zip64.zip \
   *   -
   * ```
   */
  @Test
  fun zip64() {
    val zipPath = base / "zip64.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    assertThat(zipFileSystem.read("-".toPath()) { readUtf8() })
      .isEqualTo("Android")
  }

  /**
   * Confirm we can read zip files with a full-archive comment, even if this comment is not surfaced
   * in our API.
   *
   * ```
   * echo "Android" > a.txt
   *
   * zip \
   *   --archive-comment \
   *   zipWithArchiveComment.zip \
   *   a.txt
   * ```
   */
  @Test
  fun zipWithArchiveComment() {
    val zipPath = base / "zipWithArchiveComment.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    assertThat(zipFileSystem.read("a.txt".toPath()) { readUtf8() })
      .isEqualTo("Android")
  }

  /**
   * ```
   * echo "(...128 KiB...)" > large_file.txt
   *
   * zip \
   *   --split-size \
   *   64k \
   *   cannotReadZipWithSpanning.zip \
   *   large_file.txt
   * ```
   */
  @Test
  fun cannotReadZipWithSpanning() {
    // Spanned archives must be at least 64 KiB.
    val zipPath = base / "cannotReadZipWithSpanning.zip"
    assertFailsWith<IOException> {
      fileSystem.openZip(zipPath)
    }
  }

  /**
   * ```
   * echo "Android" > a.txt
   *
   * zip \
   *   --password \
   *   secret \
   *   cannotReadZipWithEncryption.zip \
   *   a.txt
   * ```
   */
  @Test
  fun cannotReadZipWithEncryption() {
    val zipPath = base / "cannotReadZipWithEncryption.zip"
    assertFailsWith<IOException> {
      fileSystem.openZip(zipPath)
    }
  }

  /**
   * ```
   * echo "Android" > a.txt
   *
   * zip \
   *   zipTooShort.zip \
   *   a.txt
   * ```
   */
  @Test
  fun zipTooShort() {
    val zipPath = base / "zipTooShort.zip"

    val prefix = fileSystem.read(zipPath) { readByteString(20) }
    fileSystem.write(zipPath) { write(prefix) }

    assertFailsWith<IOException> {
      fileSystem.openZip(zipPath)
    }
  }

  /**
   * The zip format permits multiple files with the same names. For example,
   * `kotlin-gradle-plugin-1.5.20.jar` contains two copies of
   * `META-INF/kotlin-gradle-statistics.kotlin_module`.
   *
   * We used to crash on duplicates, but they are common in practice so now we prefer the last
   * entry. This behavior is consistent with both `java.util.zip.ZipFile` and
   * `java.nio.file.FileSystem`.
   *
   * ```
   * echo "This is the first hello.txt" > hello.txt
   *
   * echo "This is the second hello.txt" > xxxxx.xxx
   *
   * zip \
   *   filesOverlap.zip \
   *   hello.txt \
   *   xxxxx.xxx
   * ```
   */
  @Test
  fun filesOverlap() {
    val zipPath = base / "filesOverlap.zip"
    val original = fileSystem.read(zipPath) { readByteString() }
    val rewritten = original.replaceAll("xxxxx.xxx".encodeUtf8(), "hello.txt".encodeUtf8())
    fileSystem.write(zipPath) { write(rewritten) }

    val zipFileSystem = fileSystem.openZip(zipPath)
    assertThat(zipFileSystem.read("hello.txt".toPath()) { readUtf8() })
      .isEqualTo("This is the second hello.txt")
    assertThat(zipFileSystem.list("/".toPath()))
      .containsExactly("/hello.txt".toPath())
  }

  /**
   * ```
   * echo "Hello World" > hello.txt
   *
   * mkdir -p directory
   * echo "Another file!" > directory/child.txt
   *
   * zip \
   *   canonicalizationValid.zip \
   *   hello.txt \
   *   directory/child.txt
   * ```
   */
  @Test
  fun canonicalizationValid() {
    val zipPath = base / "canonicalizationValid.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    assertThat(zipFileSystem.canonicalize("/".toPath())).isEqualTo("/".toPath())
    assertThat(zipFileSystem.canonicalize(".".toPath())).isEqualTo("/".toPath())
    assertThat(zipFileSystem.canonicalize("not/a/path/../../..".toPath())).isEqualTo("/".toPath())
    assertThat(zipFileSystem.canonicalize("hello.txt".toPath())).isEqualTo("/hello.txt".toPath())
    assertThat(zipFileSystem.canonicalize("stuff/../hello.txt".toPath())).isEqualTo("/hello.txt".toPath())
    assertThat(zipFileSystem.canonicalize("directory".toPath())).isEqualTo("/directory".toPath())
    assertThat(zipFileSystem.canonicalize("directory/whevs/..".toPath())).isEqualTo("/directory".toPath())
    assertThat(zipFileSystem.canonicalize("directory/child.txt".toPath())).isEqualTo("/directory/child.txt".toPath())
    assertThat(zipFileSystem.canonicalize("directory/whevs/../child.txt".toPath())).isEqualTo("/directory/child.txt".toPath())
  }

  /**
   * ```
   * echo "Hello World" > hello.txt
   *
   * mkdir -p directory
   * echo "Another file!" > directory/child.txt
   *
   * zip \
   *   canonicalizationInvalidThrows.zip \
   *   hello.txt \
   *   directory/child.txt
   * ```
   */
  @Test
  fun canonicalizationInvalidThrows() {
    val zipPath = base / "canonicalizationInvalidThrows.zip"
    val zipFileSystem = fileSystem.openZip(zipPath)

    assertFailsWith<FileNotFoundException> {
      zipFileSystem.canonicalize("not/a/path".toPath())
    }
  }
}

private fun ByteString.replaceAll(a: ByteString, b: ByteString): ByteString {
  val buffer = Buffer()
  buffer.write(this)
  buffer.replace(a, b)
  return buffer.readByteString()
}

private fun Buffer.replace(a: ByteString, b: ByteString) {
  val result = Buffer()
  while (!exhausted()) {
    val index = indexOf(a)
    if (index == -1L) {
      result.writeAll(this)
    } else {
      result.write(this, index)
      result.write(b)
      skip(a.size.toLong())
    }
  }
  writeAll(result)
}

/** Decodes this ISO8601 time string. */
fun String.toEpochMillis() = Instant.parse(this).toEpochMilliseconds()
