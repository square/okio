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
package okio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.datetime.Clock
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class ForwardingFileSystemTest : AbstractFileSystemTest(
  clock = Clock.System,
  fileSystem = object : ForwardingFileSystem(FakeFileSystem().apply { emulateUnix() }) {},
  windowsLimitations = false,
  allowClobberingEmptyDirectories = false,
  allowAtomicMoveFromFileToDirectory = false,
  temporaryDirectory = "/".toPath(),
) {
  @Test
  fun pathBlocking() {
    val forwardingFileSystem = object : ForwardingFileSystem(fileSystem) {
      override fun delete(path: Path, mustExist: Boolean) {
        throw IOException("synthetic failure!")
      }

      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        if (path.name.contains("blocked")) throw IOException("blocked path!")
        return path
      }
    }

    forwardingFileSystem.createDirectory(base / "okay")
    assertFailsWith<IOException> {
      forwardingFileSystem.createDirectory(base / "blocked")
    }
  }

  @Test
  fun operationBlocking() {
    val forwardingFileSystem = object : ForwardingFileSystem(fileSystem) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        if (functionName == "delete") throw IOException("blocked operation!")
        return path
      }
    }

    forwardingFileSystem.createDirectory(base / "operation-blocking")
    assertFailsWith<IOException> {
      forwardingFileSystem.delete(base / "operation-blocking")
    }
  }

  @Test
  fun pathMapping() {
    val prefix = "/mapped"
    val source = base / "source"
    val mappedSource = (prefix + source).toPath()
    val target = base / "target"
    val mappedTarget = (prefix + target).toPath()

    source.writeUtf8("hello, world!")

    val forwardingFileSystem = object : ForwardingFileSystem(fileSystem) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        return path.toString().removePrefix(prefix).toPath()
      }

      override fun onPathResult(path: Path, functionName: String): Path {
        return (prefix + path).toPath()
      }
    }

    forwardingFileSystem.copy(mappedSource, mappedTarget)
    assertTrue(target in fileSystem.list(base))
    assertTrue(mappedTarget in forwardingFileSystem.list(base))
    assertEquals("hello, world!", source.readUtf8())
    assertEquals("hello, world!", target.readUtf8())
  }

  /**
   * Path mapping might impact the sort order. Confirm that list() returns elements in sorted order
   * even if that order is different in the delegate file system.
   */
  @Test
  fun pathMappingImpactedBySorting() {
    val az = base / "az"
    val by = base / "by"
    val cx = base / "cx"
    az.writeUtf8("az")
    by.writeUtf8("by")
    cx.writeUtf8("cx")

    val forwardingFileSystem = object : ForwardingFileSystem(fileSystem) {
      override fun onPathResult(path: Path, functionName: String): Path {
        return path.parent!! / path.name.reversed()
      }
    }

    assertEquals(listOf(base / "az", base / "by", base / "cx"), fileSystem.list(base))
    assertEquals(listOf(base / "xc", base / "yb", base / "za"), forwardingFileSystem.list(base))
  }

  @Test
  fun copyIsNotForwarded() {
    val log = mutableListOf<String>()

    val delegate = object : ForwardingFileSystem(fileSystem) {
      override fun copy(source: Path, target: Path) {
        throw AssertionError("unexpected call to copy()")
      }
    }

    val forwardingFileSystem = object : ForwardingFileSystem(delegate) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        log += "$functionName($parameterName=$path)"
        return path
      }
    }

    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    forwardingFileSystem.copy(source, target)
    assertTrue(target in fileSystem.list(base))
    assertEquals("hello, world!", source.readUtf8())
    assertEquals("hello, world!", target.readUtf8())

    assertEquals(listOf("source(file=$source)", "sink(file=$target)"), log)
  }

  @Test
  fun metadataForwardsParameterAndSymlinkTarget() {
    val log = mutableListOf<String>()

    val forwardingFileSystem = object : ForwardingFileSystem(fileSystem) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        log += "$functionName($parameterName=$path)"
        return path
      }

      override fun onPathResult(path: Path, functionName: String): Path {
        log += "$functionName($path)"
        return path
      }
    }

    val target = base / "symlink-target"
    val source = base / "symlink-source"

    fileSystem.createSymlink(source, target)

    val sourceMetadata = forwardingFileSystem.metadata(source)
    assertEquals(target, sourceMetadata.symlinkTarget)

    assertEquals(listOf("metadataOrNull(path=$source)", "metadataOrNull($target)"), log)
  }
}
