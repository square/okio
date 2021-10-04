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
import kotlin.time.ExperimentalTime
import kotlinx.datetime.Clock
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

@ExperimentalTime
@ExperimentalFileSystem
class ForwardingFileSystemTest : AbstractFileSystemTest(
  clock = Clock.System,
  fileSystem = object : ForwardingFileSystem(FakeFileSystem().apply { emulateUnix() }) {},
  windowsLimitations = false,
  allowClobberingEmptyDirectories = false,
  temporaryDirectory = "/".toPath()
) {
  @Test
  fun pathBlocking() {
    val forwardingFileSystem = object : ForwardingFileSystem(fileSystem) {
      override fun delete(path: Path) {
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

    assertEquals(fileSystem.list(base), listOf(base / "az", base / "by", base / "cx"))
    assertEquals(forwardingFileSystem.list(base), listOf(base / "xc", base / "yb", base / "za"))
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

    assertEquals(log, listOf("source(file=$source)", "sink(file=$target)"))
  }

  @Test
  fun listRecursively() {
    val logs = ArrayDeque<String>()

    val forwardingFileSystem = object : ForwardingFileSystem(fileSystem) {
      override fun onPathParameter(path: Path, functionName: String, parameterName: String): Path {
        logs += "$functionName($parameterName=$path)"
        return path
      }
    }

    data class Tree(val path: Path, val isFile: Boolean)

    val preExistingPaths = listOf(
      Tree(base, isFile = false),
      Tree("$base/root.txt".toPath(), isFile = true),
      Tree("$base/dirA".toPath(), isFile = false),
      Tree("$base/dirA/file.txt".toPath(), isFile = true),
      Tree("$base/dirA/child1".toPath(), isFile = false),
      Tree("$base/dirA/child2".toPath(), isFile = false),
      Tree("$base/dirA/child2/file.txt".toPath(), isFile = true),
      Tree("$base/dirB".toPath(), isFile = false),
    )

    for (preExistingPath in preExistingPaths) {
      if (preExistingPath.path == base) continue

      if (preExistingPath.isFile) {
        fileSystem.write(preExistingPath.path) {
          writeUtf8("file ${preExistingPath.path.name}")
        }
      } else {
        fileSystem.createDirectory(preExistingPath.path)
      }
    }

    val expectedPaths = ArrayDeque(listOf(
      "$base/dirA".toPath(),
      "$base/dirA/file.txt".toPath(),
      "$base/dirA/child1".toPath(),
      "$base/dirA/child2".toPath(),
      "$base/dirA/child2/file.txt".toPath(),
      "$base/dirB".toPath(),
      "$base/dirB/1".toPath(),
      "$base/dirB/2".toPath(),
      "$base/dirB/3".toPath(),
      "$base/root.txt".toPath(),
    ))

    val listRecursive = forwardingFileSystem.listRecursively(base)
    for (path in listRecursive) {
      logs += "Path returned: $path"
      // TODO(Benoit) Check the order by asserting `path == expected.removeFirst()` ?
      assertTrue(expectedPaths.remove(path))

      // At some arbitrary point, we create other files which should be picked up by our
      // `listRecursively` call if this one is lazy.
      if (path == base / "dirA/child2") {
        val somethingsB = listOf(
          Tree("$base/dirB/1".toPath(), isFile = true),
          Tree("$base/dirB/2".toPath(), isFile = true),
          Tree("$base/dirB/3".toPath(), isFile = true),
        )
        for (something in somethingsB) {
          fileSystem.write(something.path) {
            writeUtf8("file ${something.path.name}")
          }
        }
      }
    }

    assertTrue(expectedPaths.isEmpty(), "Unconsummed paths: ${expectedPaths.joinToString()}")
  }
}
