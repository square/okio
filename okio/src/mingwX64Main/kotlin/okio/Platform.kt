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

import kotlinx.cinterop.toKString
import okio.Path.Companion.toPath
import platform.posix.getenv

internal actual val PLATFORM_TEMPORARY_DIRECTORY: Path
  get() {
    // Windows' built-in APIs check the TEMP, TMP, and USERPROFILE environment variables in order.
    // https://docs.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-gettemppatha?redirectedfrom=MSDN
    val temp = getenv("TEMP")
    if (temp != null) return temp.toKString().toPath()

    val tmp = getenv("TMP")
    if (tmp != null) return tmp.toKString().toPath()

    val userProfile = getenv("USERPROFILE")
    if (userProfile != null) return userProfile.toKString().toPath()

    return "\\Windows\\TEMP".toPath()
  }
