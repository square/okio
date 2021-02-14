package okio.zipfilesystem

import okio.ByteString.Companion.toByteString
import okio.ExperimentalFileSystem
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.sink
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

@ExperimentalFileSystem
class ZipFileSystemTest {
  private val fileSystem = FileSystem.SYSTEM
  private var base = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / randomToken()

  @Before
  fun setUp() {
    fileSystem.createDirectory(base)
  }

  @Test
  fun testToString() {
    val zipPath = base / "file.zip"
    writeZipFile(
      zipPath = zipPath,
      "hello.txt" to "Hello World",
      "directory/subdirectory/child.txt" to "Another file!",
    )
    val zipFileSystem = open(zipPath, fileSystem)

    assertThat(zipFileSystem.toString()).isEqualTo("ZipFileSystem[$zipPath]")
  }

  @Test
  fun readFiles() {
    val zipPath = base / "file.zip"
    writeZipFile(
      zipPath = zipPath,
      "hello.txt" to "Hello World",
      "directory/subdirectory/child.txt" to "Another file!",
    )
    val zipFileSystem = open(zipPath, fileSystem)

    assertThat(zipFileSystem.read("hello.txt".toPath()) { readUtf8() })
      .isEqualTo("Hello World")

    assertThat(zipFileSystem.read("directory/subdirectory/child.txt".toPath()) { readUtf8() })
      .isEqualTo("Another file!")
  }

  private fun writeZipFile(zipPath: Path, vararg files: Pair<String, String>) {
    fileSystem.write(zipPath) {
      ZipOutputStream(this.outputStream()).use { zip ->
        for ((entryName, entryContent) in files) {
          zip.putNextEntry(ZipEntry(entryName))
          zip.sink().buffer().apply {
            writeUtf8(entryContent)
            emit()
          }
        }
      }
    }
  }
}

fun randomToken() = Random.nextBytes(16).toByteString(0, 16).hex()
