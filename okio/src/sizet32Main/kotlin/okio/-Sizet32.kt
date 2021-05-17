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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import platform.posix.FILE
import platform.posix.fileno
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.pread
import platform.posix.pwrite

internal actual fun variantFread(
  target: CPointer<ByteVarOf<Byte>>,
  byteCount: UInt,
  file: CPointer<FILE>
): UInt {
  return fread(target, 1, byteCount, file)
}

internal actual fun variantFwrite(
  source: CPointer<ByteVar>,
  byteCount: UInt,
  file: CPointer<FILE>
): UInt {
  return fwrite(source, 1, byteCount, file)
}

internal actual fun variantPread(
  file: CPointer<FILE>,
  target: CValuesRef<*>,
  byteCount: Int,
  offset: Long
): Int {
  return pread(fileno(file), target, byteCount.toUInt(), offset)
}

internal actual fun variantPwrite(
  file: CPointer<FILE>,
  source: CValuesRef<*>,
  byteCount: Int,
  offset: Long
): Int {
  return pwrite(fileno(file), source, byteCount.toUInt(), offset)
}
