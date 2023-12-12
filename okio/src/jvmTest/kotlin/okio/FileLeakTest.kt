/*
 * Copyright (C) 2023 Square, Inc. and others.
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

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test

class FileLeakTest {

  private lateinit var fakeFileSystem: FakeFileSystem
  private val fakeZip = "/test.zip".toPath()
  private val fakeEntry = "some.file".toPath()
  private val fakeDirectory = "/another/".toPath()
  private val fakeEntry2 = fakeDirectory / "another.file"

  @Before
  fun setup() {
    fakeFileSystem = FakeFileSystem()
    with(fakeFileSystem) {
      write(fakeZip) {
        writeZip {
          putEntry(fakeEntry.name) {
            writeUtf8("FooBar")
          }
          try {
            putNextEntry(ZipEntry(fakeDirectory.name).apply { time = 0L })
          } finally {
            closeEntry()
          }
          putEntry(fakeEntry2.toString()) {
            writeUtf8("SomethingElse")
          }
        }
      }
    }
  }

  @After
  fun tearDown() {
    fakeFileSystem.checkNoOpenFiles()
  }

  @Test
  fun zipFileSystemExistsTest() {
    val zipFileSystem = fakeFileSystem.openZip(fakeZip)
    assertTrue(zipFileSystem.exists(fakeEntry))
  }

  @Test
  fun zipFileSystemMetadataTest() {
    val zipFileSystem = fakeFileSystem.openZip(fakeZip)
    assertNotNull(zipFileSystem.metadataOrNull(fakeEntry))
  }

  @Test
  fun zipFileSystemSourceTest() {
    val zipFileSystem = fakeFileSystem.openZip(fakeZip)
    zipFileSystem.source(fakeEntry).use { source ->
      assertEquals("FooBar", source.buffer().readUtf8())
    }
  }

  @Test
  fun zipFileSystemListRecursiveTest() {
    val zipFileSystem = fakeFileSystem.openZip(fakeZip)
    zipFileSystem.listRecursively("/".toPath()).toList()
    fakeFileSystem.delete(fakeZip)
  }
}

/**
 * Writes a ZIP file to a [BufferedSink].
 */
private inline fun <R> BufferedSink.writeZip(action: ZipOutputStream.() -> R): R {
  return ZipOutputStream(outputStream()).use(action)
}

/**
 * Adds a new ZIP entry named [name], populates it with [action], and closes the entry.
 */
private inline fun <R> ZipOutputStream.putEntry(name: String, action: BufferedSink.() -> R): R {
  putNextEntry(ZipEntry(name).apply { time = 0L })
  val sink = sink().buffer()
  return try {
    sink.action()
  } finally {
    sink.flush()
    closeEntry()
  }
}
