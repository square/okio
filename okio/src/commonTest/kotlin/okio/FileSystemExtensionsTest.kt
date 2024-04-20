/*
 * Copyright (C) 2024 Square, Inc.
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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import okio.FileSystemExtension.Mapping
import okio.fakefilesystem.FakeFileSystem

class FileSystemExtensionsTest {
  @Test
  fun happyPath() {
    val fakeFileSystem = FakeFileSystem()
    val extension = FooExtension(fakeFileSystem)
    val fileSystemWithExtension = fakeFileSystem.extend(extension)
    assertEquals(fileSystemWithExtension.extension<FooExtension>(), extension)
  }

  @Test
  fun absentExtension() {
    val fakeFileSystem = FakeFileSystem()
    val extension = FooExtension(fakeFileSystem)
    val fileSystemWithExtension = fakeFileSystem.extend(extension)
    assertNull(fileSystemWithExtension.extension<BarExtension>())
  }

  @Test
  fun overrideExtension() {
    val fakeFileSystem = FakeFileSystem()
    val extension1 = FooExtension(fakeFileSystem)
    val fileSystemWithExtension1 = fakeFileSystem.extend(extension1)

    val extension2 = FooExtension(fakeFileSystem)
    val fileSystemWithExtension2 = fileSystemWithExtension1.extend(extension2)
    assertEquals(fileSystemWithExtension2.extension<FooExtension>(), extension2)

    // Doesn't interfere with any of the wrapped layers.
    assertEquals(fileSystemWithExtension1.extension<FooExtension>(), extension1)
    assertNull(fakeFileSystem.extension<FooExtension>(), null)
  }

  @Test
  fun forwardingFileSystemCoalesced() {
    val fakeFileSystem = FakeFileSystem()
    val fooExtension = FooExtension(fakeFileSystem)
    val fileSystemWithFoo = fakeFileSystem.extend(fooExtension)

    val barExtension = BarExtension(fakeFileSystem)
    val fileSystemWithFooAndBar = fileSystemWithFoo.extend(barExtension)

    assertEquals(fileSystemWithFooAndBar.extension<FooExtension>(), fooExtension)
    assertEquals(fileSystemWithFooAndBar.extension<BarExtension>(), barExtension)
    assertEquals((fileSystemWithFooAndBar as ForwardingFileSystem).delegate, fakeFileSystem)
  }

  @Test
  fun customForwardingFileSystemNotCoalesced() {
    val fakeFileSystem = FakeFileSystem()
    val fooExtension = FooExtension(fakeFileSystem)
    val fileSystemWithFoo = object : ForwardingFileSystem(
      delegate = fakeFileSystem,
      extensions = mapOf(FooExtension::class to fooExtension),
    ) {
      // This extends ForwardingFileSystem. Usually this would be to add new capabilities.
    }

    val barExtension = BarExtension(fakeFileSystem)
    val fileSystemWithFooAndBar = fileSystemWithFoo.extend(barExtension)

    assertEquals(fileSystemWithFooAndBar.extension<FooExtension>(), fooExtension)
    assertEquals(fileSystemWithFooAndBar.extension<BarExtension>(), barExtension)
    assertNotEquals((fileSystemWithFooAndBar as ForwardingFileSystem).delegate, fakeFileSystem)
  }

  class FooExtension(
    val target: FileSystem,
  ) : FileSystemExtension {
    override fun map(outer: Mapping) = this
  }

  class BarExtension(
    val target: FileSystem,
  ) : FileSystemExtension {
    override fun map(outer: Mapping) = this
  }
}
