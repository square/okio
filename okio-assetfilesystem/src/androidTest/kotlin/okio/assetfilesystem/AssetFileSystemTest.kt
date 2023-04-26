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
package okio.assetfilesystem

import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.assertFailsWith
import okio.Buffer
import okio.BufferedSource
import okio.FileHandle
import okio.FileNotFoundException
import okio.IOException
import okio.Path.Companion.toPath
import okio.buffer
import org.junit.Test

class AssetFileSystemTest {
  private val context = InstrumentationRegistry.getInstrumentation().context
  private val fs = context.assets.asFileSystem()

  @Test fun canonicalizeValid() {
    assertThat(fs.canonicalize("/".toPath())).isEqualTo("/".toPath())
    assertThat(fs.canonicalize(".".toPath())).isEqualTo("/".toPath())
    assertThat(fs.canonicalize("not/a/path/../../..".toPath())).isEqualTo("/".toPath())
    assertThat(fs.canonicalize("file.txt".toPath())).isEqualTo("/file.txt".toPath())
    assertThat(fs.canonicalize("stuff/../file.txt".toPath())).isEqualTo("/file.txt".toPath())
    assertThat(fs.canonicalize("dir".toPath())).isEqualTo("/dir".toPath())
    assertThat(fs.canonicalize("dir/whevs/..".toPath())).isEqualTo("/dir".toPath())
    assertThat(fs.canonicalize("dir/nested.txt".toPath())).isEqualTo("/dir/nested.txt".toPath())
    assertThat(fs.canonicalize("dir/whevs/../nested.txt".toPath())).isEqualTo("/dir/nested.txt".toPath())
  }

  @Test fun canonicalizeInvalidThrows() {
    assertFailsWith<FileNotFoundException> {
      fs.canonicalize("not/a/path".toPath())
    }
  }

  @Test fun listRoot() {
    val list = fs.list("/".toPath())
    assertThat(list).containsAll(
      "dir".toPath(),
      "file.txt".toPath(),
      "moby10b.txt".toPath(),
    )
  }

  @Test fun listRootCanonicalizes() {
    val list = fs.list("foo/bar/../..".toPath())
    assertThat(list).containsAll(
      "dir".toPath(),
      "file.txt".toPath(),
      "moby10b.txt".toPath(),
    )
  }

  @Test fun listDirectory() {
    val list = fs.list("dir".toPath())
    assertThat(list).containsExactly("nested.txt".toPath())
  }

  @Test fun listDirectoryCanonicalizes() {
    val list = fs.list("dir/not/real/../..".toPath())
    assertThat(list).containsExactly("nested.txt".toPath())
  }

  @Test fun listNonExistentDirectoryThrows() {
    assertFailsWith<FileNotFoundException> {
      fs.list("nope/".toPath())
    }
  }

  @Test fun listFileThrows() {
    assertFailsWith<FileNotFoundException> {
      fs.list("dir/nested.txt".toPath())
    }
  }

  @Test fun listOrNullDirectory() {
    val list = fs.listOrNull("dir".toPath())
    assertThat(list).isNotNull().containsExactly("nested.txt".toPath())
  }

  @Test fun listOrNullDirectoryCanonicalizes() {
    val list = fs.listOrNull("dir/not/real/../..".toPath())
    assertThat(list).isNotNull().containsExactly("nested.txt".toPath())
  }

  @Test fun listOrNullNonExistentDirectory() {
    val list = fs.listOrNull("nope".toPath())
    assertThat(list).isNull()
  }

  @Test fun listOrNullFile() {
    val list = fs.listOrNull("dir/nested.txt".toPath())
    assertThat(list).isNull()
  }

  @Test fun metadataFile() {
    val metadata = fs.metadataOrNull("file.txt".toPath())!!

    // Data we can get:
    assertThat(metadata.isRegularFile).isTrue()
    assertThat(metadata.isDirectory).isFalse()

    // Data we cannot get or is impossible:
    assertThat(metadata.size).isNull()
    assertThat(metadata.symlinkTarget).isNull()
    assertThat(metadata.createdAtMillis).isNull()
    assertThat(metadata.lastModifiedAtMillis).isNull()
    assertThat(metadata.lastAccessedAtMillis).isNull()
    assertThat(metadata.extras).isEmpty()
  }

  @Test fun metadataDirectory() {
    val metadata = fs.metadataOrNull("dir".toPath())!!

    // Data we can get:
    assertThat(metadata.isRegularFile).isFalse()
    assertThat(metadata.isDirectory).isTrue()

    // Data we cannot get or is impossible:
    assertThat(metadata.symlinkTarget).isNull()
    assertThat(metadata.size).isNull()
    assertThat(metadata.createdAtMillis).isNull()
    assertThat(metadata.lastModifiedAtMillis).isNull()
    assertThat(metadata.lastAccessedAtMillis).isNull()
    assertThat(metadata.extras).isEmpty()
  }

  @Test fun metadataDirectoryCanonicalizes() {
    val metadata = fs.metadataOrNull("dir/not/real/../..".toPath())!!
    assertThat(metadata.isDirectory).isTrue()
  }

  @Test fun metadataNonExistentPath() {
    val metadata = fs.metadataOrNull("not/a/path".toPath())
    assertThat(metadata).isNull()
  }

  @Test fun sourceFile() {
    val file = fs.source("file.txt".toPath()).buffer().use(BufferedSource::readUtf8)
    assertThat(file).isEqualTo("File!\n")
    val nested = fs.source("dir/nested.txt".toPath()).buffer().use(BufferedSource::readUtf8)
    assertThat(nested).isEqualTo("Nested!\n")
  }

  @Test fun sourceDirectory() {
    assertFailsWith<FileNotFoundException> {
      fs.source("dir".toPath())
    }
  }

  @Test fun sourceNonExistent() {
    assertFailsWith<FileNotFoundException> {
      fs.source("not/a/path".toPath())
    }
  }

  @Test fun openReadOnlyInvalidThrows() {
    assertFailsWith<FileNotFoundException> {
      fs.openReadOnly("not/a/path".toPath())
    }
  }

  @Test fun openReadOnlyDirectoryThrows() {
    assertFailsWith<FileNotFoundException> {
      fs.openReadOnly("dir".toPath())
    }
  }

  @Test fun openReadOnlySize() {
    val smallSize = fs.openReadOnly("file.txt".toPath()).use(FileHandle::size)
    assertThat(smallSize).isEqualTo(6)

    val mobySize = fs.openReadOnly("moby10b.txt".toPath()).use(FileHandle::size)
    assertThat(mobySize).isEqualTo(1232923)
  }

  @Test fun openReadOnlyRandomAccessForward() {
    fs.openReadOnly("moby10b.txt".toPath()).use { handle ->
      val buffer = Buffer()
      handle.read(34251, buffer, 16)
      assertThat(buffer.readUtf8()).isEqualTo("Call me Ishmael.")
      handle.read(148605, buffer, 49)
      assertThat(buffer.readUtf8()).isEqualTo("It is not down in any map; true places never are.")
      handle.read(1051694, buffer, 50)
      assertThat(buffer.readUtf8()).isEqualTo("his forehead's veins swelled like overladen brooks")
      handle.read(148605, buffer, 49)
      assertThat(buffer.readUtf8()).isEqualTo("It is not down in any map; true places never are.")
      handle.read(34251, buffer, 16)
      assertThat(buffer.readUtf8()).isEqualTo("Call me Ishmael.")
    }
  }

  @Test fun openReadOnlyRandomAccessThenSize() {
    fs.openReadOnly("moby10b.txt".toPath()).use { handle ->
      val buffer = Buffer()
      handle.read(34251, buffer, 16)
      assertThat(buffer.readUtf8()).isEqualTo("Call me Ishmael.")

      assertThat(handle.size()).isEqualTo(1232923)
    }
  }

  @Test fun openReadOnlyFlushThrows() {
    fs.openReadOnly("file.txt".toPath()).use { handle ->
      val t = assertFailsWith<IllegalStateException> {
        handle.flush()
      }
      assertThat(t).hasMessage("file handle is read-only")
    }
  }

  @Test fun openReadOnlyResizeThrows() {
    fs.openReadOnly("file.txt".toPath()).use { handle ->
      val t = assertFailsWith<IllegalStateException> {
        handle.resize(10L)
      }
      assertThat(t).hasMessage("file handle is read-only")
    }
  }

  @Test fun openReadOnlyWriteThrows() {
    fs.openReadOnly("file.txt".toPath()).use { handle ->
      val t = assertFailsWith<IllegalStateException> {
        handle.write(0, Buffer().writeUtf8("Sup"), 3)
      }
      assertThat(t).hasMessage("file handle is read-only")
    }
  }

  @Test fun sinkThrows() {
    val t = assertFailsWith<IOException> {
      fs.sink("file.txt".toPath())
    }
    assertThat(t).hasMessage("asset file systems are read-only")
  }

  @Test fun appendingSinkThrows() {
    val t = assertFailsWith<IOException> {
      fs.appendingSink("file.txt".toPath())
    }
    assertThat(t).hasMessage("asset file systems are read-only")
  }

  @Test fun createDirectoryThrows() {
    val t = assertFailsWith<IOException> {
      fs.createDirectory("new-dir".toPath(), mustCreate = true)
    }
    assertThat(t).hasMessage("asset file systems are read-only")
  }

  @Test fun atomicMoveThrows() {
    val t = assertFailsWith<IOException> {
      fs.atomicMove("file.txt".toPath(), "new-file.txt".toPath())
    }
    assertThat(t).hasMessage("asset file systems are read-only")
  }

  @Test fun deleteThrows() {
    val t = assertFailsWith<IOException> {
      fs.delete("file.txt".toPath())
    }
    assertThat(t).hasMessage("asset file systems are read-only")
  }

  @Test fun createSymlinkThrows() {
    val t = assertFailsWith<IOException> {
      fs.createSymlink("file.txt".toPath(), "new-file.txt".toPath())
    }
    assertThat(t).hasMessage("asset file systems are read-only")
  }

  @Test fun openReadWriteThrows() {
    val t = assertFailsWith<IOException> {
      fs.openReadWrite("file.txt".toPath())
    }
    assertThat(t).hasMessage("asset file systems are read-only")
  }
}
