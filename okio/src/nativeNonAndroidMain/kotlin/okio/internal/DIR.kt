/*
 * Copyright (C) 2026 Square, Inc.
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
package okio.internal

import kotlinx.cinterop.CValuesRef

internal actual typealias DIR = platform.posix.DIR
internal actual fun opendir(path: String) = platform.posix.opendir(path)
internal actual fun readdir(dir: CValuesRef<DIR>) = platform.posix.readdir(dir)
internal actual fun closedir(dir: CValuesRef<DIR>) = platform.posix.closedir(dir)
