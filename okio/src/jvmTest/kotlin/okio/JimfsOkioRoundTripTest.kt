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
package okio

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.nio.file.StandardOpenOption
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import okio.FileSystem.Companion.asOkioFileSystem
import org.junit.Test

class JimfsOkioRoundTripTest {
  private val temporaryDirectory = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
  private val jimFs = Jimfs.newFileSystem(
    when (Path.DIRECTORY_SEPARATOR == "\\") {
      true -> Configuration.windows()
      false -> Configuration.unix()
    }.toBuilder()
      .setWorkingDirectory(temporaryDirectory.toString())
      .build(),
  )
  private val jimFsRoot = jimFs.rootDirectories.first()
  private val okioFs = jimFs.asOkioFileSystem()
  private val base: Path = temporaryDirectory / "${this::class.simpleName}-${randomToken(16)}"

  @BeforeTest
  fun setUp() {
    okioFs.createDirectory(base)
  }

  @Test
  fun writeOkioReadJim() {
    val path = base / "file-handle-write-okio-and-read-jim"

    okioFs.write(path) {
      writeUtf8("abcdefghijklmnop")
    }

    assertEquals("abcdefghijklmnop", jimFsRoot.resolve(path.toString()).readText(Charsets.UTF_8))
  }

  @Test
  fun writeJimReadOkio() {
    val path = base / "file-handle-write-jim-and-read-okio"
    jimFsRoot.resolve(path.toString()).writeText("abcdefghijklmnop", Charsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE)

    okioFs.openReadWrite(path).use { handle ->
      handle.source().buffer().use { source ->
        assertEquals("abcde", source.readUtf8(5))
        assertEquals("fghijklmnop", source.readUtf8())
      }
    }
  }
}
