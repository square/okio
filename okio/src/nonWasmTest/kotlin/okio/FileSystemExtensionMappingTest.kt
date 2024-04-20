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
import okio.FileSystemExtension.Mapping
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

class FileSystemExtensionMappingTest {
  private val rawFileSystem = FakeFileSystem()

  @Test
  fun happyPath() {
    // Create a bunch of files in the /monday directory.
    val mondayExtension = ChefExtension(rawFileSystem)
    val mondayFs = rawFileSystem.extend(mondayExtension)
    mondayFs.createDirectory("/monday".toPath())
    mondayFs.write("/monday/breakfast.txt".toPath()) { writeUtf8("croissant") }
    mondayFs.write("/monday/lunch.txt".toPath()) { writeUtf8("peanut butter sandwich") }
    mondayFs.write("/monday/dinner.txt".toPath()) { writeUtf8("pizza") }

    // Associate data with these files in the extension.
    mondayExtension.setChef("/monday/dinner.txt".toPath(), "jesse")
    mondayExtension.setChef("/monday/breakfast.txt".toPath(), "benoit")
    mondayExtension.setChef("/monday/lunch.txt".toPath(), "jesse")

    // Confirm we can query the extension.
    assertEquals(
      listOf(
        "/monday/dinner.txt".toPath(),
        "/monday/lunch.txt".toPath(),
      ),
      mondayExtension.listForChef("/monday".toPath(), "jesse"),
    )
    assertEquals(
      listOf(
        "/monday/breakfast.txt".toPath(),
      ),
      mondayExtension.listForChef("/monday".toPath(), "benoit"),
    )

    // Apply a path mapping transformation and confirm we can read the metadata.
    val tuesdayFs = TuesdayFileSystem(mondayFs)
    val tuesdayExtension = tuesdayFs.extension<ChefExtension>()!!
    assertEquals(
      listOf(
        "/tuesday/dinner.txt".toPath(),
        "/tuesday/lunch.txt".toPath(),
      ),
      tuesdayExtension.listForChef("/tuesday".toPath(), "jesse"),
    )
    assertEquals(
      listOf(
        "/tuesday/breakfast.txt".toPath(),
      ),
      tuesdayExtension.listForChef("/tuesday".toPath(), "benoit"),
    )

    // We should also be able to write through the extension...
    tuesdayFs.write("/tuesday/snack.txt".toPath()) { writeUtf8("doritos") }
    tuesdayExtension.setChef("/tuesday/snack.txt".toPath(), "jake")
    assertEquals(
      listOf(
        "/tuesday/snack.txt".toPath(),
      ),
      tuesdayExtension.listForChef("/tuesday".toPath(), "jake"),
    )

    // ...And the extension data should map all the way through to the source file system.
    assertEquals(
      listOf(
        "/monday/snack.txt".toPath(),
      ),
      mondayExtension.listForChef("/monday".toPath(), "jake"),
    )
  }

  @Test
  fun chainTransformations() {
    val mondayExtension = ChefExtension(rawFileSystem)
    val mondayFs = rawFileSystem.extend(mondayExtension)
    mondayFs.createDirectory("/monday".toPath())
    mondayFs.write("/monday/breakfast.txt".toPath()) { writeUtf8("croissant") }
    mondayExtension.setChef("/monday/breakfast.txt".toPath(), "benoit")

    // Map /monday to /tuesday.
    val tuesdayFs = TuesdayFileSystem(mondayFs)

    // Map / to /menu.
    val menuFs = MenuFileSystem(tuesdayFs)
    val menuExtension = menuFs.extension<ChefExtension>()!!

    // Confirm we can read through the mappings.
    assertEquals(
      listOf(
        "/menu/tuesday/breakfast.txt".toPath(),
      ),
      menuExtension.listForChef("/menu/tuesday".toPath(), "benoit"),
    )

    // Confirm we can write through also.
    menuFs.write("/menu/tuesday/lunch.txt".toPath()) { writeUtf8("tomato soup") }
    menuExtension.setChef("/menu/tuesday/lunch.txt".toPath(), "jesse")
    assertEquals(
      "tomato soup",
      mondayFs.read("/monday/lunch.txt".toPath()) { readUtf8() },
    )
    assertEquals(
      listOf(
        "/monday/lunch.txt".toPath(),
      ),
      mondayExtension.listForChef("/monday".toPath(), "jesse"),
    )

    // Each extension gets its own mapping.
    assertEquals(
      "tomato soup",
      tuesdayFs.read("/tuesday/lunch.txt".toPath()) { readUtf8() },
    )
    assertEquals(
      listOf(
        "/tuesday/lunch.txt".toPath(),
      ),
      tuesdayFs.extension<ChefExtension>()!!.listForChef("/tuesday".toPath(), "jesse"),
    )
  }

  /**
   * This test extension associates paths with optional metadata: the chef of a file.
   *
   * When tests run there will be multiple instances of this extension, all sharing one [chefs]
   * store, and each with its own [mapping]. The contents of [chefs] uses mapped paths for its keys.
   *
   * Real world extensions will have similar obligations for path mapping, but they'll likely do
   * real things with the paths such as passing them to system APIs.
   */
  class ChefExtension(
    private val target: FileSystem,
    private val chefs: MutableMap<Path, String> = mutableMapOf(),
    private val mapping: Mapping = Mapping.NONE,
  ) : FileSystemExtension {
    override fun map(outer: Mapping) = ChefExtension(target, chefs, mapping.chain(outer))

    fun setChef(path: Path, chef: String) {
      val mappedPath = mapping.mapParameter(path, "set", "path")
      chefs[mappedPath] = chef
    }

    fun listForChef(dir: Path, chef: String): List<Path> {
      val mappedDir = mapping.mapParameter(dir, "listForChef", "dir")
      return target.list(mappedDir)
        .filter { chefs[it] == chef }
        .map { mapping.mapResult(it, "listForChef") }
    }
  }

  class TuesdayFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
    private val monday = "/monday".toPath()
    private val tuesday = "/tuesday".toPath()

    override fun onPathParameter(path: Path, functionName: String, parameterName: String) =
      monday / path.relativeTo(tuesday)

    override fun onPathResult(path: Path, functionName: String) =
      tuesday / path.relativeTo(monday)
  }

  class MenuFileSystem(delegate: FileSystem) : ForwardingFileSystem(delegate) {
    private val root = "/".toPath()
    private val menu = "/menu".toPath()

    override fun onPathParameter(path: Path, functionName: String, parameterName: String) =
      root / path.relativeTo(menu)

    override fun onPathResult(path: Path, functionName: String) =
      menu / path.relativeTo(root)
  }
}
