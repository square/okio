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

import kotlinx.datetime.Clock
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFilesystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalFilesystem
class ForwardingFilesystemTest : AbstractFilesystemTest(
  clock = Clock.System,
  filesystem = object : ForwardingFilesystem(FakeFilesystem()) {},
  windowsLimitations = false,
  temporaryDirectory = "/".toPath()
) {
  @Test
  fun pathBlocking() {
    val forwardingFilesystem = object : ForwardingFilesystem(filesystem) {
      override fun delete(path: Path) {
        throw IOException("synthetic failure!")
      }

      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        if (path.name.contains("blocked")) throw IOException("blocked path!")
        return path
      }
    }

    forwardingFilesystem.createDirectory(base / "okay")
    assertFailsWith<IOException> {
      forwardingFilesystem.createDirectory(base / "blocked")
    }
  }

  @Test
  fun operationBlocking() {
    val forwardingFilesystem = object : ForwardingFilesystem(filesystem) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        if (functionName == "delete") throw IOException("blocked operation!")
        return path
      }
    }

    forwardingFilesystem.createDirectory(base / "operation-blocking")
    assertFailsWith<IOException> {
      forwardingFilesystem.delete(base / "operation-blocking")
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

    val forwardingFilesystem = object : ForwardingFilesystem(filesystem) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        return path.toString().removePrefix(prefix).toPath()
      }

      override fun onPathResult(path: Path, functionName: String): Path {
        return (prefix + path).toPath()
      }
    }

    forwardingFilesystem.copy(mappedSource, mappedTarget)
    assertTrue(target in filesystem.list(base))
    assertTrue(mappedTarget in forwardingFilesystem.list(base))
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

    val forwardingFilesystem = object : ForwardingFilesystem(filesystem) {
      override fun onPathResult(path: Path, functionName: String): Path {
        return path.parent!! / path.name.reversed()
      }
    }

    assertEquals(filesystem.list(base), listOf(base / "az", base / "by", base / "cx"))
    assertEquals(forwardingFilesystem.list(base), listOf(base / "xc", base / "yb", base / "za"))
  }

  @Test
  fun copyIsNotForwarded() {
    val log = mutableListOf<String>()

    val delegate = object : ForwardingFilesystem(filesystem) {
      override fun copy(source: Path, target: Path) {
        throw AssertionError("unexpected call to copy()")
      }
    }

    val forwardingFilesystem = object : ForwardingFilesystem(delegate) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        log += "$functionName($parameterName=$path)"
        return path
      }
    }

    val source = base / "source"
    source.writeUtf8("hello, world!")
    val target = base / "target"
    forwardingFilesystem.copy(source, target)
    assertTrue(target in filesystem.list(base))
    assertEquals("hello, world!", source.readUtf8())
    assertEquals("hello, world!", target.readUtf8())

    assertEquals(log, listOf("source(file=$source)", "sink(file=$target)"))
  }
}
