/*
 * Copyright (C) 2022 Square, Inc.
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

import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileNotFoundException
import okio.Path.Companion.toPath
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class AssetFileSystemTest {
  private val context = InstrumentationRegistry.getInstrumentation().context
  private val fs = AssetFileSystem(context.assets)

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
    assertThatThrownBy {
      fs.canonicalize("not/a/path".toPath())
    }.isInstanceOf(FileNotFoundException::class.java)
  }

  @Test fun listRoot() {
    val list = fs.list("/".toPath())
    assertThat(list).containsExactlyInAnyOrder(
      "dir".toPath(),
      "file.txt".toPath(),
      "images".toPath(),
      "webkit".toPath(),
    )
  }

  @Test fun listRootCanonicalizes() {
    val list = fs.list("foo/bar/../..".toPath())
    assertThat(list).containsExactlyInAnyOrder(
      "dir".toPath(),
      "file.txt".toPath(),
      "images".toPath(),
      "webkit".toPath(),
    )
  }

  @Test fun listDirectory() {
    val list = fs.list("dir".toPath())
    assertThat(list).containsExactlyInAnyOrder("nested.txt".toPath())
  }

  @Test fun listDirectoryCanonicalizes() {
    val list = fs.list("dir/not/real/../..".toPath())
    assertThat(list).containsExactlyInAnyOrder("nested.txt".toPath())
  }

  @Test fun listNonExistentDirectoryThrows() {
    assertThatThrownBy {
      fs.list("nope/".toPath())
    }.isInstanceOf(FileNotFoundException::class.java)
  }

  @Test fun listFileThrows() {
    assertThatThrownBy {
      fs.list("dir/nested.txt".toPath())
    }.isInstanceOf(FileNotFoundException::class.java)
  }

  @Test fun listOrNullDirectory() {
    val list = fs.listOrNull("dir".toPath())
    assertThat(list).containsExactlyInAnyOrder("nested.txt".toPath())
  }

  @Test fun listOrNullDirectoryCanonicalizes() {
    val list = fs.listOrNull("dir/not/real/../..".toPath())
    assertThat(list).containsExactlyInAnyOrder("nested.txt".toPath())
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
    assertThatThrownBy {
      fs.source("dir".toPath())
    }.isInstanceOf(FileNotFoundException::class.java)
  }

  @Test fun sourceNonExistent() {
    assertThatThrownBy {
      fs.source("not/a/path".toPath())
    }.isInstanceOf(FileNotFoundException::class.java)
  }
}
