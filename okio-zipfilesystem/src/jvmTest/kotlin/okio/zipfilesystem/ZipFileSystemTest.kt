package okio.zipfilesystem

import kotlinx.datetime.Instant
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
        entries += ZipBuilder.Entry(
          path = "a.txt",
          content = "Android",
          modifiedAt = "200102030405.06",
          accessedAt = "200102030405.07"
        )
        entries += ZipBuilder.Entry(
          path = "b.txt",
          content = "Banana",
          modifiedAt = "200908070605.04",
          accessedAt = "200908070605.03"
        )
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

  /** Confirm we suffer UNIX limitations on our date format. */
  @Test
  fun zipWithFileOutOfBoundsModifiedDate() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry(
          path = "a.txt",
          content = "Android",
          modifiedAt = "196912310000.00",
          accessedAt = "196912300000.00"
        )
        entries += ZipBuilder.Entry(
          path = "b.txt",
          content = "Banana",
          modifiedAt = "203801190314.07", // Last UNIX date representable in 31 bits.
          accessedAt = "203801190314.08" // Overflows!
        )
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

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
   */
  @Test
  fun zipWithDirectoryModifiedDate() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry("a/a.txt", "Android")
        entries += ZipBuilder.Entry(
          path = "a",
          directory = true,
          modifiedAt = "200102030405.06",
          accessedAt = "200102030405.07"
        )
        entries += ZipBuilder.Entry("b/b.txt", "Android")
        entries += ZipBuilder.Entry(
          path = "b",
          directory = true,
          modifiedAt = "200908070605.04",
          accessedAt = "200908070605.03"
        )
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

  @Test
  fun zipWithModifiedDate() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry(
          "a/a.txt",
          modifiedAt = "197001010001.00",
          accessedAt = "197001010002.00",
          content = "Android"
        )
      }
      .build()
    val zipFileSystem = open(zipPath, fileSystem)

    zipFileSystem.metadata("a/a.txt".toPath())
      .apply {
        assertThat(createdAtMillis).isNull()
        assertThat(lastModifiedAtMillis).isEqualTo("1970-01-01T00:01:00Z".toEpochMillis())
        assertThat(lastAccessedAtMillis).isEqualTo("1970-01-01T00:02:00Z".toEpochMillis())
      }
  }

  /** Build a very small zip file with just a single empty directory. */
  @Test
  fun zipWithEmptyDirectory() {
    val zipPath = ZipBuilder(base)
      .apply {
        entries += ZipBuilder.Entry(
          path = "a",
          directory = true,
          modifiedAt = "200102030405.06",
          accessedAt = "200102030405.07"
        )
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
        assertThat(lastAccessedAtMillis).isEqualTo("2001-02-03T04:05:07Z".toEpochMillis())
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
        entries += ZipBuilder.Entry("a", directory = true)
        entries += ZipBuilder.Entry("b/b.txt", "Android")
        entries += ZipBuilder.Entry("b", directory = true)
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

fun randomToken(length: Int = 16) = Random.nextBytes(length).toByteString().hex()
