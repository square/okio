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
import okio.FileSystem
import okio.FileSystemExtension
import okio.FileSystemExtension.Mapping
import okio.Path
import okio.extension

/**
 * Install this extension to add support for [FileSystem.openSqlite].
 *
 * Set inMemory to true for `FakeFileSystem` and false for `FileSystem.SYSTEM`.
 */
class SqliteExtension private constructor(
  internal val mapping: Mapping,
  internal val inMemory: Boolean,
) : FileSystemExtension {
  constructor(inMemory: Boolean = false) : this(Mapping.NONE, inMemory)

  override fun map(outer: Mapping) = SqliteExtension(mapping.chain(outer), inMemory)
}

fun FileSystem.openSqlite(
  path: Path,
  properties: Properties = Properties(),
): Connection {
  val extension = extension<SqliteExtension>()
    ?: error("This file system doesn't have the SqliteExtension")

  val mappedPath = extension.mapping.mapParameter(path, "openSqlite", "path")
  val url = when {
    extension.inMemory -> "jdbc:sqlite:file:$mappedPath?mode=memory&cache=shared"
    else -> "jdbc:sqlite:$mappedPath"
  }

  return DriverManager.getConnection(url, properties)
}
