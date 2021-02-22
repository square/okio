package okio.zipfilesystem

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toInstant
import okio.ByteString.Companion.toByteString
import okio.ExperimentalFileSystem
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertFailsWith

@ExperimentalFileSystem
class ZipFileSystemTest {
  private val fileSystem = FileSystem.SYSTEM
  private var base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / randomToken()

  @Before
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test
  fun zipWithFiles() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("hello.txt", "Hello World")
        entries += ZipBuilder.Entry("directory/subdirectory/child.txt", "Another file!")
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    assertThat(zipFileSystem.read("hello.txt".toPath()) { readUtf8() })
      .isEqualTo("Hello World")

    assertThat(zipFileSystem.read("directory/subdirectory/child.txt".toPath()) { readUtf8() })
      .isEqualTo("Another file!")

    assertThat(zipFileSystem.list("/".toPath()))
      .hasSameElementsAs(listOf("/hello.txt".toPath(), "/directory".toPath()))
    assertThat(zipFileSystem.list("/directory".toPath()))
      .containsExactly("/directory/subdirectory".toPath())
    assertThat(zipFileSystem.list("/directory/subdirectory".toPath()))
      .containsExactly("/directory/subdirectory/child.txt".toPath())
  }

  /**
   * Note that the zip tool does not compress files that don't benefit from it. Examples above like
   * 'Hello World' are stored, not deflated.
   */
  @Test
  fun zipWithDeflate() {
    val content = "Android\n".repeat(1000)
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a.txt", content)
        options += "--compression-method"
        options += "deflate"
      }
      .build()
    assertThat(fileSystem.metadata(zipPath).size).isLessThan(content.length.toLong())
    val zipFileSystem = open(zipPath, fileSystem)

    assertThat(zipFileSystem.read("a.txt".toPath()) { readUtf8() })
      .isEqualTo(content)
  }

  @Test
  fun zipWithStore() {
    val content = "Android\n".repeat(1000)
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a.txt", content)
        options += "--compression-method"
        options += "store"
      }
      .build()
    assertThat(fileSystem.metadata(zipPath).size).isGreaterThan(content.length.toLong())
    val zipFileSystem = open(zipPath, fileSystem)

    assertThat(zipFileSystem.read("a.txt".toPath()) { readUtf8() })
      .isEqualTo(content)
  }

  /**
   * Confirm we can read zip files that have file comments, even if these comments are not exposed
   * in the public API.
   */
  @Test
  fun zipWithFileComments() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a.txt", "Android", comment = "A is for Android")
        entries += ZipBuilder.Entry("b.txt", "Banana", comment = "B or not to Be")
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    assertThat(zipFileSystem.read("a.txt".toPath()) { readUtf8() })
      .isEqualTo("Android")

    assertThat(zipFileSystem.read("b.txt".toPath()) { readUtf8() })
      .isEqualTo("Banana")
  }

  @Test
  fun zipWithFileModifiedDate() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a.txt", "Android", modifiedAt = "200102030405.06")
        entries += ZipBuilder.Entry("b.txt", "Banana", modifiedAt = "200908070605.04")
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    zipFileSystem.metadata("a.txt".toPath())
      .apply {
        assertThat(isRegularFile).isTrue()
        assertThat(isDirectory).isFalse()
        assertThat(size).isEqualTo(7L)
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2001-02-03T04:05:06Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isNull()
      }

    zipFileSystem.metadata("b.txt".toPath())
      .apply {
        assertThat(isRegularFile).isTrue()
        assertThat(isDirectory).isFalse()
        assertThat(size).isEqualTo(6L)
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2009-08-07T06:05:04Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isNull()
      }
  }

  /**
   * Confirm handling the limitations of DOS dates as they're stored in zip files. The dates printed
   * by the 'unzip' command don't have these limitations! When we implement support for NTFS and
   * UNIX extensions neither should we.
   */
  @Test
  fun zipWithFileOutOfBoundsModifiedDate() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a.txt", "Android", modifiedAt = "197912310000.00")
        entries += ZipBuilder.Entry("b.txt", "Banana", modifiedAt = "210801020000.00")
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    // Lower than the lower bound (1980-01-01) returns the lower bound.
    zipFileSystem.metadata("a.txt".toPath())
      .apply {
        assertThat(isRegularFile).isTrue()
        assertThat(isDirectory).isFalse()
        assertThat(size).isEqualTo(7L)
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("1980-01-01T00:00:00".toLocalEpochMillis())
        assertThat(lastAccessedAtMillis).isNull()
      }

    // Greater than the upper bound (2108-01-01) wraps around.
    zipFileSystem.metadata("b.txt".toPath())
      .apply {
        assertThat(isRegularFile).isTrue()
        assertThat(isDirectory).isFalse()
        assertThat(size).isEqualTo(6L)
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("1980-01-02T00:00:00Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isNull()
      }
  }

  /**
   * Directories are optional in the zip file. But if we want metadata on them they must be stored.
   * Note that this test adds the directories last; otherwise adding child files to them will cause
   * their modified at times to change.
   */
  @Test
  fun zipWithDirectoryModifiedDate() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a/a.txt", "Android")
        entries += ZipBuilder.Entry("a", directory = true, modifiedAt = "200102030405.06")
        entries += ZipBuilder.Entry("b/b.txt", "Android")
        entries += ZipBuilder.Entry("b", directory = true, modifiedAt = "200908070605.04")
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    zipFileSystem.metadata("a".toPath())
      .apply {
        assertThat(isRegularFile).isFalse()
        assertThat(isDirectory).isTrue()
        assertThat(size).isNull()
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2001-02-03T04:05:06Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isNull()
      }
    assertThat(zipFileSystem.list("a".toPath())).containsExactly("/a/a.txt".toPath())

    zipFileSystem.metadata("b".toPath())
      .apply {
        assertThat(isRegularFile).isFalse()
        assertThat(isDirectory).isTrue()
        assertThat(size).isNull()
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2009-08-07T06:05:04Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isNull()
      }
    assertThat(zipFileSystem.list("b".toPath())).containsExactly("/b/b.txt".toPath())
  }

  /** Build a very small zip file with just a single empty directory. */
  @Test
  fun zipWithEmptyDirectory() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a", directory = true, modifiedAt = "200102030405.06")
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    zipFileSystem.metadata("a".toPath())
      .apply {
        assertThat(isRegularFile).isFalse()
        assertThat(isDirectory).isTrue()
        assertThat(size).isNull()
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("2001-02-03T04:05:06Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isNull()
      }
    assertThat(zipFileSystem.list("a".toPath())).isEmpty()
  }

  /**
   * The `--no-dir-entries` option causes the zip file to omit the directories from the encoded
   * file. Our implementation synthesizes these missing directories automatically.
   */
  @Test
  fun zipWithSyntheticDirectory() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a/a.txt", "Android")
        entries += ZipBuilder.Entry("a", directory = true, modifiedAt = "200102030405.06")
        entries += ZipBuilder.Entry("b/b.txt", "Android")
        entries += ZipBuilder.Entry("b", directory = true, modifiedAt = "200908070605.04")
        options += "--no-dir-entries"
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

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
   */
  @Test
  fun zip64() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("-", "Android", zip64 = true)
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    assertThat(zipFileSystem.read("-".toPath()) { readUtf8() })
      .isEqualTo("Android")
  }

  /**
   * Confirm we can read zip files with a full-archive comment, even if this comment is not surfaced
   * in our API.
   */
  @Test
  fun zipWithArchiveComment() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a.txt", "Android")
        archiveComment = "this comment applies to the entire archive"
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    assertThat(zipFileSystem.read("a.txt".toPath()) { readUtf8() })
      .isEqualTo("Android")
  }

  @Test
  fun cannotReadZipWithSpanning() {
    // Spanned archives must be at least 64 KiB.
    val largeFile = randomToken(length = 128 * 1024)
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("large_file.txt", largeFile)
        options += "--split-size"
        options += "64k"
      }
      .build()
    assertFailsWith<IOException> {
      open(zipPath, fileSystem)
    }
  }

  @Test
  fun cannotReadZipWithEncryption() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a.txt", "Android")
        options += "--password"
        options += "secret"
      }
      .build()
    assertFailsWith<IOException> {
      open(zipPath, fileSystem)
    }
  }

  @Test
  fun zipTooShort() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a.txt", "Android")
      }
      .build()

    val prefix = fileSystem.read(zipPath) { readByteString(20) }
    fileSystem.write(zipPath) { write(prefix) }

    assertFailsWith<IOException> {
      open(zipPath, fileSystem)
    }
  }
}

/** Decodes this ISO8601 time string. */
fun String.toEpochMillis() = Instant.parse(this).toEpochMilliseconds()

/** Decodes this ISO8601 local time string using the machine's time zone. */
fun String.toLocalEpochMillis(): Long {
  val localDateTime = LocalDateTime.parse(this)
  val localInstant = localDateTime.toInstant(currentSystemDefault())
  return localInstant.toEpochMilliseconds()
}

fun randomToken(length: Int = 16) = Random.nextBytes(length).toByteString().hex()
