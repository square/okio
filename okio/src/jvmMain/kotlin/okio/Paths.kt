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
@file:JvmName("Paths")
package okio

import okio.Path.Companion.toPath
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
import java.io.File
import java.nio.file.Paths
import java.nio.file.Path as NioPath

@ExperimentalFilesystem
fun Path.toFile(): File = File(toString())

@IgnoreJRERequirement // Can only be invoked on platforms that have java.nio.file.
@ExperimentalFilesystem
fun Path.toNioPath(): NioPath = Paths.get(toString())

@ExperimentalFilesystem
fun File.toOkioPath(): Path = toString().toPath()

@IgnoreJRERequirement // Can only be invoked on platforms that have java.nio.file.
@ExperimentalFilesystem
fun NioPath.toOkioPath(): Path = toString().toPath()
