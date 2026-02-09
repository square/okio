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

import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import platform.posix.dirent

/**
 * `platform.posix.DIR` is not available on Android Native, so the standard
 * POSIX directory APIs (`opendir`, `readdir`, `closedir`) cannot be used
 * directly.
 *
 * These expect declarations provide platform-specific implementations
 * for all `DIR`-related functionality.
 */
internal expect class DIR : CPointed
internal expect fun opendir(path: String): CPointer<DIR>?
internal expect fun readdir(dir: CValuesRef<DIR>): CPointer<dirent>?
internal expect fun closedir(dir: CValuesRef<DIR>): Int
