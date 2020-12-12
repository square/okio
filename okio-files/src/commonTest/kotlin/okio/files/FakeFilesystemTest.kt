/*
 * Copyright (C) 2020 Square, Inc.
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
package okio.files

import okio.FakeFilesystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.minutes

@ExperimentalTime
class FakeWindowsFilesystemTest : FakeFilesystemTest(
  clock = FakeClock(),
  windowsLimitations = true
)

@ExperimentalTime
class FakeUnixFilesystemTest : FakeFilesystemTest(
  clock = FakeClock(),
  windowsLimitations = false
)

@ExperimentalTime
abstract class FakeFilesystemTest internal constructor(
  clock: FakeClock,
  windowsLimitations: Boolean
) : AbstractFilesystemTest(
  clock = clock,
  filesystem = FakeFilesystem(clock, windowsLimitations),
  windowsLimitations = windowsLimitations,
  temporaryDirectory = "/".toPath(),
) {
  private val fakeFilesystem: FakeFilesystem = filesystem as FakeFilesystem
  private val fakeClock: FakeClock = clock

  @Test
  fun `open paths includes open sink`() {
    val openPath = base / "open-file"
    val sink = filesystem.sink(openPath)
    assertEquals(openPath, fakeFilesystem.openPaths.single())
    sink.close()
    assertTrue(fakeFilesystem.openPaths.isEmpty())
  }

  @Test
  fun `open paths includes open source`() {
    val openPath = base / "open-file"
    openPath.writeUtf8("hello, world!")
    assertTrue(fakeFilesystem.openPaths.isEmpty())
    val source = filesystem.source(openPath)
    assertEquals(openPath, fakeFilesystem.openPaths.single())
    source.close()
    assertTrue(fakeFilesystem.openPaths.isEmpty())
  }

  @Test
  fun `file last accessed time`() {
    val path = base / "file-last-accessed-time"

    fakeClock.sleep(1.minutes)
    path.writeUtf8("hello, world!")
    val createdAt = clock.now()

    fakeClock.sleep(1.minutes)
    path.writeUtf8("hello again!")
    val modifiedAt = clock.now()

    fakeClock.sleep(1.minutes)
    path.readUtf8()
    val accessedAt = clock.now()

    val metadata = filesystem.metadata(path)
    assertEquals(createdAt, metadata.createdAt)
    assertEquals(modifiedAt, metadata.lastModifiedAt)
    assertEquals(accessedAt, metadata.lastAccessedAt)
  }

  @Test
  fun `directory last accessed time`() {
    val path = base / "directory-last-accessed-time"

    fakeClock.sleep(1.minutes)
    filesystem.createDirectory(path)
    val createdAt = clock.now()

    fakeClock.sleep(1.minutes)
    (path / "child").writeUtf8("hello world!")
    val modifiedAt = clock.now()

    fakeClock.sleep(1.minutes)
    filesystem.list(path)
    val accessedAt = clock.now()

    val metadata = filesystem.metadata(path)
    assertEquals(createdAt, metadata.createdAt)
    assertEquals(modifiedAt, metadata.lastModifiedAt)
    assertEquals(accessedAt, metadata.lastAccessedAt)
  }
}
