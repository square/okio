/*
 * Copyright (C) 2026 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress(
  "INVISIBLE_REFERENCE",
)

package okio

import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import okio.internal.ErrnoException
import okio.internal.preview1.environ_get
import okio.internal.preview1.environ_sizes_get
import okio.internal.readString

@OptIn(UnsafeWasmMemoryApi::class)
val env: Map<String, String> by lazy {
  withScopedMemoryAllocator { allocator ->
    val entryCountPointer = allocator.allocate(4)
    val byteCountPointer = allocator.allocate(4)
    val sizesGetErrno = environ_sizes_get(
      returnEntryCountPointer = entryCountPointer.address.toInt(),
      returnByteCountPointer = byteCountPointer.address.toInt(),
    )
    if (sizesGetErrno != 0) throw ErrnoException(sizesGetErrno.toShort())

    val entryCount = entryCountPointer.loadInt()
    val byteCount = byteCountPointer.loadInt()

    val entryPointersPointer = allocator.allocate(entryCount * 4)
    val entryBytesPointer = allocator.allocate(byteCount)
    val getErrno = environ_get(
      entryPointersPointer.address.toInt(),
      entryBytesPointer.address.toInt(),
    )
    if (getErrno != 0) throw ErrnoException(getErrno.toShort())

    buildMap {
      for (i in 0 until entryCount) {
        val entryPointer = Pointer((entryPointersPointer + i * 4).loadInt().toUInt())
        val entryString = entryPointer.readNullTerminated()
        val eq = entryString.indexOf('=')
        if (eq != -1) {
          put(entryString.take(eq), entryString.substring(eq + 1))
        }
      }
    }
  }
}

@OptIn(UnsafeWasmMemoryApi::class)
internal fun Pointer.readNullTerminated(): String {
  var byteCount = 0
  while ((this + byteCount).loadByte().toInt() != 0) {
    byteCount++
  }
  return readString(byteCount)
}

actual fun getEnv(name: String) = env[name]
