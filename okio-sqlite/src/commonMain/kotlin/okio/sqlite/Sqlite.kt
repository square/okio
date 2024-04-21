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
import java.sql.DriverManager
import java.util.Properties
import kotlin.reflect.KClass
import kotlin.reflect.cast
import okio.FileSystem
import okio.FileSystemExtension
import okio.FileSystemExtension.Mapping
import okio.ForwardingFileSystem
import okio.Path
import okio.extend
import okio.extension

/**
 * Returns an extended file system that supports [FileSystem.openSqlite].
 *
 * @param inMemory true for `FakeFileSystem` and false for [FileSystem.SYSTEM].
 */
fun FileSystem.withSqlite(inMemory: Boolean): FileSystem {
  return when {
    inMemory -> InMemorySqliteFileSystem(this)
    else -> extend<SqliteExtension>(SystemSqliteExtension(Mapping.NONE))
  }
}

/**
 * Opens a connection to the database at [path], creating it if it doesn't exist.
 */
fun FileSystem.openSqlite(
  path: Path,
  properties: Properties = Properties(),
): Connection {
  val extension = extension<SqliteExtension>()
    ?: error("This file system doesn't have the SqliteExtension")

  val mappedPath = extension.mapping.mapParameter(path, "openSqlite", "path")

  return extension.openSqlite(mappedPath, properties)
}

private interface SqliteExtension : FileSystemExtension {
  val mapping: Mapping

  fun openSqlite(path: Path, properties: Properties): Connection
}

private class SystemSqliteExtension(
  override val mapping: Mapping,
) : SqliteExtension {
  override fun map(outer: Mapping) = SystemSqliteExtension(mapping.chain(outer))

  override fun openSqlite(path: Path, properties: Properties) =
    DriverManager.getConnection("jdbc:sqlite:$path", properties)
}

private class InMemorySqliteExtension(
  override val mapping: Mapping,
  private val fileSystem: InMemorySqliteFileSystem,
) : SqliteExtension {
  override fun map(outer: Mapping) = InMemorySqliteExtension(mapping.chain(outer), fileSystem)

  override fun openSqlite(path: Path, properties: Properties) =
    fileSystem.openSqlite(path, properties)
}

/**
 * SQLite permits multiple in-memory databases, each named by a unique identifier. When all
 * connections to a particular in-memory database are closed, that database is discarded.
 *
 * This file system simulates a persistent database by creating a sentinel file on the file system
 * to stand in for the database plus an extra connection to the in-memory database. If the sentinel
 * file is ever deleted, this closes the extra connection to the in-memory database. That allows
 * SQLite to discard the database.
 *
 * Aside from delete, this file system doesn't support other operations on the database file.
 * Moving it or appending to it may break the connection to the in-memory database.
 */
private class InMemorySqliteFileSystem(
  delegate: FileSystem,
) : ForwardingFileSystem(delegate) {
  // TODO(jwilson): create a way to close a FileSystem, so these may also be closed.
  private val openDbs = mutableMapOf<Path, OpenDb>()
  private val sqliteExtension = InMemorySqliteExtension(Mapping.NONE, this)

  fun openSqlite(
    path: Path,
    properties: Properties,
  ): Connection {
    val openDb = openDbs.getOrPut(path) {
      write(path) {
        writeUtf8("Use FileSystem.openSqlite() to read this database")
      }
      OpenDb("jdbc:sqlite:file:${nextOpenDbId++}?mode=memory&cache=shared")
    }

    return DriverManager.getConnection(openDb.url, properties)
  }

  override fun delete(path: Path, mustExist: Boolean) {
    super.delete(path, mustExist)
    openDbs.remove(path)?.reserveConnection?.close()
  }

  override fun <E : FileSystemExtension> extension(type: KClass<E>): E? {
    if (type == SqliteExtension::class) return type.cast(sqliteExtension)
    return delegate.extension(type)
  }

  private class OpenDb(val url: String) {
    /** Keep a connection open until the file is deleted. */
    val reserveConnection = DriverManager.getConnection(url)
  }

  companion object {
    private var nextOpenDbId = 1
  }
}
