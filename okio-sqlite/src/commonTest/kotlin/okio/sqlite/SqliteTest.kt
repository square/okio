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
package okio.sqlite

import java.sql.Connection
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import okio.randomToken
import org.junit.Test

class SqliteTest {
  @Test
  fun inMemory() {
    val rawFileSystem = FakeFileSystem()
    val fileSystem = rawFileSystem.withSqlite(inMemory = true)
    val databasePath = "/pizza.db".toPath()

    fileSystem.openSqlite(databasePath).use { connection ->
      connection.createToppingsTable()
      connection.insertToppings()
      connection.assertToppingsPresent()
    }
  }

  @Test
  fun onDisk() {
    val rawFileSystem = FileSystem.SYSTEM
    val temp = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / randomToken(16)
    val fileSystem = rawFileSystem.withSqlite(inMemory = false)
    fileSystem.createDirectory(temp)

    val databasePath = temp / "pizza.db"

    fileSystem.openSqlite(databasePath).use { connection ->
      connection.createToppingsTable()
      connection.insertToppings()
    }

    // Data is still there after it's closed.
    fileSystem.openSqlite(databasePath).use { connection ->
      connection.assertToppingsPresent()
    }

    assertTrue(fileSystem.exists(databasePath))
    fileSystem.delete(databasePath)
  }

  @Test
  fun multipleConnectionsSharingInMemoryDatabase() {
    val rawFileSystem = FakeFileSystem()
    val fileSystem = rawFileSystem.withSqlite(inMemory = true)
    val databasePath = "/pizza.db".toPath()

    fileSystem.openSqlite(databasePath).use { connection1 ->
      connection1.createToppingsTable()
      connection1.insertToppings()

      fileSystem.openSqlite(databasePath).use { connection2 ->
        connection2.assertToppingsPresent()
      }
    }
  }

  @Test
  fun inMemoryDataPersistedAcrossConnections() {
    val rawFileSystem = FakeFileSystem()
    val fileSystem = rawFileSystem.withSqlite(inMemory = true)
    val databasePath = "/pizza.db".toPath()

    fileSystem.openSqlite(databasePath).use { connection ->
      connection.createToppingsTable()
      connection.insertToppings()
    }

    assertTrue(fileSystem.exists("pizza.db".toPath()))

    fileSystem.openSqlite(databasePath).use { connection ->
      connection.assertToppingsPresent()
    }

    fileSystem.delete("/pizza.db".toPath())
    fileSystem.openSqlite(databasePath).use { connection ->
      connection.assertSchemaAbsent()
    }
  }

  @Test
  fun inMemoryWithMappedPath() {
    val rawFileSystem = FakeFileSystem()
    val mondayFs = rawFileSystem.withSqlite(inMemory = true)
    val tuesdayFs = MappedFileSystem(mondayFs, "/monday".toPath(), "/tuesday".toPath())
    mondayFs.createDirectory("/monday".toPath())

    mondayFs.openSqlite("/monday/pizza.db".toPath()).use { mondayConnection ->
      mondayConnection.createToppingsTable()
      mondayConnection.insertToppings()

      tuesdayFs.openSqlite("/tuesday/pizza.db".toPath()).use { tuesdayConnection ->
        tuesdayConnection.assertToppingsPresent()
      }
    }
  }

  @Test
  fun onDiskWithMappedPath() {
    val rawFileSystem = FileSystem.SYSTEM
    val temp = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / randomToken(16)
    val mondayFs = rawFileSystem.withSqlite(inMemory = false)
    val mondayDir = temp / "monday"
    mondayFs.createDirectories(mondayDir)

    val tuesdayDir = "/tuesday".toPath()
    val tuesdayFs = MappedFileSystem(mondayFs, mondayDir, tuesdayDir)

    mondayFs.openSqlite(mondayDir / "pizza.db").use { connection ->
      connection.createToppingsTable()
      connection.insertToppings()
    }

    tuesdayFs.openSqlite(tuesdayDir / "pizza.db").use { connection ->
      connection.assertToppingsPresent()
    }

    assertTrue(mondayFs.exists(mondayDir / "pizza.db"))
    mondayFs.delete(mondayDir / "pizza.db")
  }

  private fun Connection.insertToppings() {
    prepareStatement("INSERT INTO toppings (name) VALUES ('pineapple'), ('olives')").execute()
  }

  private fun Connection.createToppingsTable() {
    prepareStatement("CREATE TABLE toppings (name TEXT)").execute()
  }

  private fun Connection.assertToppingsPresent() {
    val resultSet = prepareStatement("SELECT name FROM toppings").executeQuery()

    val items = buildList<String> {
      while (resultSet.next()) {
        add(resultSet.getString("name"))
      }
    }

    assertEquals(listOf("pineapple", "olives"), items)
  }

  private fun Connection.assertSchemaAbsent() {
    val resultSet = prepareStatement(
      "SELECT name FROM sqlite_schema WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
    ).executeQuery()

    val items = buildList<String> {
      while (resultSet.next()) {
        add(resultSet.getString("name"))
      }
    }

    assertEquals(listOf(), items)
  }

  class MappedFileSystem(
    delegate: FileSystem,
    private val delegateRoot: Path,
    private val root: Path,
  ) : ForwardingFileSystem(delegate) {

    override fun onPathParameter(path: Path, functionName: String, parameterName: String) =
      delegateRoot / path.relativeTo(root)

    override fun onPathResult(path: Path, functionName: String) =
      root / path.relativeTo(delegateRoot)
  }
}
