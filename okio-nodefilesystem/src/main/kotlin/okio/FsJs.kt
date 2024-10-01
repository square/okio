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

/**
 * This class declares the subset of Node.js file system APIs that we need in Okio.
 *
 *
 * Why not Dukat?
 * --------------
 *
 * This file does manually what ideally [Dukat] would do automatically.
 *
 * Dukat's generated stubs need awkward call sites to disambiguate overloads. For example, to call
 * `mkdirSync()` we must specify an options parameter even though we just want the default:
 *
 *   mkdirSync(dir.toString(), options = undefined as MakeDirectoryOptions?)
 *
 * By defining our own externals, we can omit the unwanted optional parameter from the declaration.
 * This leads to nicer calling code!
 *
 *   mkdirSync(dir.toString())
 *
 * Dukat also gets the nullability wrong for `Dirent.readSync()`.
 *
 *
 * Why not Kotlinx-nodejs?
 * -----------------------
 *
 * Even better than using Dukat directly would be to use the [official artifact][kotlinx_nodejs],
 * itself generated with Dukat. We also don't use the official Node.js artifact for the reasons
 * above, and also because it has an unstable API.
 *
 *
 * Updating this file
 * ------------------
 *
 * To declare new external APIs, run Dukat to generate a full set of Node stubs. The easiest way to
 * do this is to add an NPM dependency on `@types/node` in `jsMain`, like this:
 *
 * ```kotlin
 * jsMain {
 *   ...
 *   dependencies {
 *     implementation(npm("@types/node", "14.14.16", true))
 *     ...
 *   }
 * }
 * ```
 *
 * This will create a file with a full set of APIs to copy-paste from.
 *
 * ```
 * okio/build/externals/okio-parent-okio/src/fs.fs.module_node.kt
 * ```
 *
 * [Dukat]: https://github.com/kotlin/dukat
 * [kotlinx_nodejs]: https://github.com/Kotlin/kotlinx-nodejs
 */
@file:JsModule("fs")
@file:JsNonModule

package okio

import kotlin.js.Date

internal external fun closeSync(fd: Number)

internal external fun mkdirSync(path: String): String?

internal external fun openSync(path: String, flags: String): Double

internal external fun opendirSync(path: String): Dir

internal external fun readlinkSync(path: String): String

internal external fun readSync(fd: Number, buffer: ByteArray, offset: Double, length: Double, position: Double?): Double

internal external fun realpathSync(path: String): String

internal external fun renameSync(oldPath: String, newPath: String)

internal external fun rmdirSync(path: String)

internal external fun lstatSync(path: String): Stats

internal external fun fstatSync(fd: Number): Stats

internal external fun unlinkSync(path: String)

internal external fun writeSync(fd: Number, buffer: ByteArray): Double

internal external fun writeSync(fd: Number, buffer: ByteArray, offset: Double, length: Double, position: Double): Double

internal external fun ftruncateSync(fd: Number, len: Double)

internal external fun symlinkSync(target: String, path: String)

internal open external class Dir {
  open var path: String
  open fun closeSync()

  // Note that dukat's signature of readSync() returns a non-nullable Dirent; that's incorrect.
  open fun readSync(): Dirent?
}

internal open external class Dirent {
  open fun isFile(): Boolean
  open fun isDirectory(): Boolean
  open fun isBlockDevice(): Boolean
  open fun isCharacterDevice(): Boolean
  open fun isSymbolicLink(): Boolean
  open fun isFIFO(): Boolean
  open fun isSocket(): Boolean
  open var name: String
}

internal external interface StatsBase<T> {
  fun isFile(): Boolean
  fun isDirectory(): Boolean
  fun isBlockDevice(): Boolean
  fun isCharacterDevice(): Boolean
  fun isSymbolicLink(): Boolean
  fun isFIFO(): Boolean
  fun isSocket(): Boolean
  var dev: T
  var ino: T
  var mode: T
  var nlink: T
  var uid: T
  var gid: T
  var rdev: T
  var size: T
  var blksize: T
  var blocks: T
  var atimeMs: T
  var mtimeMs: T
  var ctimeMs: T
  var birthtimeMs: T
  var atime: Date
  var mtime: Date
  var ctime: Date
  var birthtime: Date
}

internal open external class Stats : StatsBase<Number> {
  override fun isFile(): Boolean
  override fun isDirectory(): Boolean
  override fun isBlockDevice(): Boolean
  override fun isCharacterDevice(): Boolean
  override fun isSymbolicLink(): Boolean
  override fun isFIFO(): Boolean
  override fun isSocket(): Boolean
  override var dev: Number
  override var ino: Number
  override var mode: Number
  override var nlink: Number
  override var uid: Number
  override var gid: Number
  override var rdev: Number
  override var size: Number
  override var blksize: Number
  override var blocks: Number
  override var atimeMs: Number
  override var mtimeMs: Number
  override var ctimeMs: Number
  override var birthtimeMs: Number
  override var atime: Date
  override var mtime: Date
  override var ctime: Date
  override var birthtime: Date
}
